package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
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

import java.time.Clock;
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
    private final VerificationCodeRepository verificationCodeRepository;
    private final Clock clock;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPreparedPasswordResetEmail(
            User user,
            Long verificationId,
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
                        id -> promotePendingToken(id, user, verificationId, verificationEmail, verificationTicket),
                        (id, e) -> handlePromotionFailureAfterDelivery(
                                id, user, verificationId, verificationEmail, verificationTicket, e)),
                tokenId);
    }

    @Transactional(propagation = Propagation.MANDATORY)
    public Long createPendingPasswordResetTokenForCurrentTransaction(
            User user, VerificationCode verificationCode, String rawToken) {
        if (passwordResetTokenRepository.existsByVerificationCodeVerificationId(
                verificationCode.getVerificationId())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        String hashedToken = tokenHashService.hashSha256(rawToken);
        LocalDateTime expiryDate = LocalDateTime.now(clock).plusHours(1);
        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .token(hashedToken)
                .user(user)
                .verificationCode(verificationCode)
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

    private void promotePendingToken(Long tokenId, User user, Long verificationId,
            String verificationEmail, String verificationTicket) {
        try {
            markTokenSentAfterInvalidatingPrevious(user, tokenId, verificationId, verificationEmail, verificationTicket);
        } catch (RuntimeException e) {
            retryPromotePendingToken(tokenId, user, verificationId, verificationEmail, verificationTicket, e);
        }
    }

    private void retryPromotePendingToken(
            Long tokenId,
            User user,
            Long verificationId,
            String verificationEmail,
            String verificationTicket,
            RuntimeException originalException) {
        log.warn("Retrying password reset token promotion for tokenId={} userId={}",
                tokenId, user.getUserId(), originalException);
        markTokenSentAfterInvalidatingPrevious(user, tokenId, verificationId, verificationEmail, verificationTicket);
    }

    private void handlePromotionFailureAfterDelivery(
            Long tokenId,
            User user,
            Long verificationId,
            String verificationEmail,
            String verificationTicket,
            RuntimeException promotionException) {
        log.error("Password reset token promotion failed after email delivery: tokenId={} userId={}",
                tokenId, user.getUserId(), promotionException);
        // Never promote a token after ticket consumption failed. The unique
        // ticket-to-token link keeps any delivered link from being duplicated.
        markCurrentTokenFailedAfterPromotionFailure(tokenId, promotionException);
        throw promotionException;
    }

    private void markTokenSentAfterInvalidatingPrevious(
            User user,
            Long tokenId,
            Long verificationId,
            String verificationEmail,
            String verificationTicket) {
        transactionTemplate.executeWithoutResult(status -> {
            verificationCodeRepository.findByIdForUpdate(verificationId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED));
            User lockedUser = userRepository.findByIdForUpdate(user.getUserId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByIdForUpdate(tokenId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));
            invalidatePreviousSentTokens(lockedUser, tokenId);
            consumePasswordResetVerificationTicket(verificationEmail, verificationTicket);
            passwordResetToken.markSent();
            passwordResetTokenRepository.save(passwordResetToken);
        });
    }

    private void consumePasswordResetVerificationTicket(String verificationEmail, String verificationTicket) {
        verificationCodeService.consumeValidatedVerificationTicket(
                verificationEmail,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
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
