package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@Slf4j
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetTokenOrchestrationService {

    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final UserRepository userRepository;
    private final AuthMailDeliveryOrchestrationService mailDeliveryOrchestrationService;
    private final TransactionTemplate transactionTemplate;
    private final TokenHashService tokenHashService;
    private final VerificationCodeService verificationCodeService;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPreparedPasswordResetEmail(
            User user,
            String verificationEmail,
            String verificationTicket,
            String recipientEmail,
            Long tokenId,
            String subject,
            String body) {
        mailDeliveryOrchestrationService.sendPrepared(
                new AuthMailDeliveryOrchestrationService.PreparedMailDeliveryCommand(
                        recipientEmail,
                        subject,
                        body,
                        id -> updateDeliveryStatus(id, false),
                        id -> promotePendingToken(id, user, verificationEmail, verificationTicket),
                        (id, e) -> handlePromotionFailureAfterDelivery(
                                id, user, verificationEmail, verificationTicket, e)),
                tokenId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createPendingPasswordResetTokenForCurrentTransaction(User user, String rawToken) {
        String hashedToken = tokenHashService.hashSha256(rawToken);
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(1);
        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .token(hashedToken)
                .user(user)
                .expiryDate(expiryDate)
                .build();
        return passwordResetTokenRepository.save(passwordResetToken).getTokenId();
    }

    public Optional<PasswordResetToken> findLatestSentCompatibleToken(User user) {
        return passwordResetTokenRepository.findLatestSentByUser(user);
    }

    private void updateDeliveryStatus(Long tokenId, boolean sent) {
        transactionTemplate.executeWithoutResult(status -> passwordResetTokenRepository.findById(tokenId)
                .ifPresent(passwordResetToken -> {
                    if (sent) {
                        passwordResetToken.markSent();
                    } else {
                        passwordResetToken.markFailed();
                    }
                    passwordResetTokenRepository.save(passwordResetToken);
                }));
    }

    private void promotePendingToken(Long tokenId, User user, String verificationEmail, String verificationTicket) {
        try {
            markTokenSentAfterInvalidatingPrevious(user, tokenId, verificationEmail, verificationTicket);
        } catch (RuntimeException e) {
            retryPromotePendingToken(tokenId, user, verificationEmail, verificationTicket, e);
        }
    }

    private void retryPromotePendingToken(
            Long tokenId,
            User user,
            String verificationEmail,
            String verificationTicket,
            RuntimeException originalException) {
        log.warn("Retrying password reset token promotion for tokenId={} userId={}",
                tokenId, user.getUserId(), originalException);
        markTokenSentAfterInvalidatingPrevious(user, tokenId, verificationEmail, verificationTicket);
    }

    private void handlePromotionFailureAfterDelivery(
            Long tokenId,
            User user,
            String verificationEmail,
            String verificationTicket,
            RuntimeException promotionException) {
        log.error("Password reset token promotion failed after email delivery: tokenId={} userId={}",
                tokenId, user.getUserId(), promotionException);
        if (isUnrecoverablePromotionFailure(promotionException)) {
            markCurrentTokenFailedAfterPromotionFailure(tokenId, promotionException);
            throw promotionException;
        }
        try {
            markCurrentTokenSent(tokenId, verificationEmail, verificationTicket);
        } catch (RuntimeException statusUpdateException) {
            promotionException.addSuppressed(statusUpdateException);
            throw promotionException;
        }
    }

    private void markTokenSentAfterInvalidatingPrevious(
            User user,
            Long tokenId,
            String verificationEmail,
            String verificationTicket) {
        transactionTemplate.executeWithoutResult(status -> {
            PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByIdForUpdate(tokenId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));
            // Token rows are locked before the user to match resetPasswordWithToken and avoid lock cycles.
            invalidatePreviousSentTokens(user, tokenId);
            userRepository.findByIdForUpdate(user.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            consumePasswordResetVerificationTicket(verificationEmail, verificationTicket);
            passwordResetToken.markSent();
            passwordResetTokenRepository.save(passwordResetToken);
        });
    }

    private void markCurrentTokenSent(Long tokenId, String verificationEmail, String verificationTicket) {
        transactionTemplate.executeWithoutResult(status -> passwordResetTokenRepository.findById(tokenId)
                .ifPresent(passwordResetToken -> {
                    consumePasswordResetVerificationTicketIfAvailable(verificationEmail, verificationTicket, tokenId);
                    passwordResetToken.markSent();
                    passwordResetTokenRepository.save(passwordResetToken);
                }));
    }

    private void consumePasswordResetVerificationTicket(String verificationEmail, String verificationTicket) {
        verificationCodeService.consumeValidatedVerificationTicket(
                verificationEmail,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
    }

    private void consumePasswordResetVerificationTicketIfAvailable(
            String verificationEmail,
            String verificationTicket,
            Long tokenId) {
        try {
            consumePasswordResetVerificationTicket(verificationEmail, verificationTicket);
        } catch (BusinessException e) {
            log.warn("Password reset ticket was already unavailable during delivery recovery: tokenId={} errorCode={}",
                    tokenId, e.getErrorCode());
        }
    }

    private boolean isUnrecoverablePromotionFailure(RuntimeException exception) {
        if (!(exception instanceof BusinessException businessException)) {
            return false;
        }
        ErrorCode errorCode = businessException.getErrorCode();
        return errorCode == ErrorCode.INVALID_PASSWORD_RESET_TOKEN
                || errorCode == ErrorCode.USER_NOT_FOUND;
    }

    private void markCurrentTokenFailedAfterPromotionFailure(Long tokenId, RuntimeException promotionException) {
        try {
            updateDeliveryStatus(tokenId, false);
        } catch (RuntimeException statusUpdateException) {
            promotionException.addSuppressed(statusUpdateException);
        }
    }

    private void invalidatePreviousSentTokens(User user, Long excludeTokenId) {
        passwordResetTokenRepository.invalidatePreviousSentUnusedTokens(user, excludeTokenId);
    }
}
