package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPasswordResetEmail(User user, String recipientEmail, String rawToken, String subject, String body) {
        String hashedToken = tokenHashService.hashSha256(rawToken);
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(1);

        mailDeliveryOrchestrationService.send(new AuthMailDeliveryOrchestrationService.MailDeliveryCommand(
                recipientEmail,
                subject,
                body,
                () -> createPendingPasswordResetToken(user, hashedToken, expiryDate),
                tokenId -> updateDeliveryStatus(tokenId, false),
                tokenId -> promotePendingToken(tokenId, user),
                (tokenId, e) -> markCurrentTokenSentAfterPromotionFailure(tokenId, user, e)));
    }

    public Optional<PasswordResetToken> findLatestSentCompatibleToken(User user) {
        return passwordResetTokenRepository.findLatestSentByUser(user);
    }

    @Transactional
    public void markTokenUsed(PasswordResetToken passwordResetToken) {
        passwordResetToken.useToken();
        passwordResetTokenRepository.save(passwordResetToken);
    }

    private Long createPendingPasswordResetToken(User user, String hashedToken, LocalDateTime expiryDate) {
        final Long[] tokenIdHolder = new Long[1];
        transactionTemplate.executeWithoutResult(status -> {
            PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                    .token(hashedToken)
                    .user(user)
                    .expiryDate(expiryDate)
                    .build();
            tokenIdHolder[0] = passwordResetTokenRepository.save(passwordResetToken).getTokenId();
        });
        return tokenIdHolder[0];
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

    private void promotePendingToken(Long tokenId, User user) {
        try {
            markTokenSentAfterInvalidatingPrevious(user, tokenId);
        } catch (RuntimeException e) {
            retryPromotePendingToken(tokenId, user, e);
        }
    }

    private void retryPromotePendingToken(Long tokenId, User user, RuntimeException originalException) {
        log.warn("Retrying password reset token promotion for tokenId={} userId={}",
                tokenId, user.getUserId(), originalException);
        markTokenSentAfterInvalidatingPrevious(user, tokenId);
    }

    private void markCurrentTokenSentAfterPromotionFailure(Long tokenId, User user, RuntimeException promotionException) {
        log.error("Password reset token promotion failed after email delivery: tokenId={} userId={}",
                tokenId, user.getUserId(), promotionException);
        try {
            updateDeliveryStatus(tokenId, true);
        } catch (RuntimeException statusUpdateException) {
            promotionException.addSuppressed(statusUpdateException);
            throw promotionException;
        }
    }

    private void markTokenSentAfterInvalidatingPrevious(User user, Long tokenId) {
        transactionTemplate.executeWithoutResult(status -> {
            userRepository.findByIdForUpdate(user.getUserId())
                    .orElseThrow(() -> new IllegalStateException("Password reset user not found"));
            invalidatePreviousSentTokens(user, tokenId);
            passwordResetTokenRepository.findById(tokenId)
                    .ifPresent(passwordResetToken -> {
                        passwordResetToken.markSent();
                        passwordResetTokenRepository.save(passwordResetToken);
                    });
        });
    }

    private void invalidatePreviousSentTokens(User user, Long excludeTokenId) {
        passwordResetTokenRepository.invalidatePreviousSentUnusedTokens(user, excludeTokenId);
    }
}
