package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final EmailService emailService;
    private final TransactionTemplate transactionTemplate;
    private final SessionTokenService sessionTokenService;
    private final TokenHashService tokenHashService;

    @Value("${cloud.aws.password-reset.frontend-url}")
    private String passwordResetFrontendUrl;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPasswordResetLink(String email) {
        if (!verificationCodeService.isVerified(email)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = getActivePasswordResetUser(email, ErrorCode.USER_NOT_FOUND);
        String rawToken = UUID.randomUUID().toString();

        String resetLink = passwordResetFrontendUrl + rawToken;
        String subject = "[noviIs] 비밀번호 재설정 링크";
        String body = "<h1>비밀번호 재설정</h1><p>아래 링크를 클릭하여 비밀번호를 재설정해 주세요.</p><p><a href=\""
                + resetLink + "\">" + resetLink + "</a></p>";

        sendPasswordResetEmail(user, user.getEmail(), rawToken, subject, body);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPasswordResetLinkByEmail(String email) {
        User user = getActivePasswordResetUser(email, ErrorCode.USER_NOT_FOUND_BY_EMAIL);
        String rawToken = UUID.randomUUID().toString();

        String resetLink = passwordResetFrontendUrl + rawToken;
        String subject = "[noviIs] 비밀번호 재설정";
        String body = "<h1>비밀번호 재설정</h1>"
                + "<p>해당 이메일로 등록된 ID: <strong>" + user.getLoginId() + "</strong></p>"
                + "<p>아래 링크를 클릭하여 비밀번호를 재설정해 주세요.</p>"
                + "<p><a href=\"" + resetLink + "\">비밀번호 재설정 링크</a></p>";

        sendPasswordResetEmail(user, user.getEmail(), rawToken, subject, body);
    }

    @Transactional
    public void resetPasswordWithToken(String rawToken, String newPassword) {
        String hashedToken = tokenHashService.hashSha256(rawToken);
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        if (!passwordResetToken.isSent()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }

        PasswordResetToken latestSentToken = findLatestSentCompatiblePasswordResetToken(passwordResetToken.getUser())
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));
        if (!latestSentToken.getTokenId().equals(passwordResetToken.getTokenId())) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }

        if (passwordResetToken.isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN);
        }
        if (passwordResetToken.getIsUsed()) {
            throw new BusinessException(ErrorCode.USED_PASSWORD_RESET_TOKEN);
        }

        User user = passwordResetToken.getUser();
        applyPasswordReset(user, newPassword);

        passwordResetToken.useToken();
        passwordResetTokenRepository.save(passwordResetToken);

        verificationCodeService.clearVerificationStatus(user.getEmail());
    }

    @Transactional
    public void resetPasswordByCode(String email, String code, String newPassword) {
        verificationCodeService.verifyCode(email, code);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }

        applyPasswordReset(user, newPassword);
        verificationCodeService.clearVerificationStatus(email);
    }

    private void applyPasswordReset(User user, String newPassword) {
        validatePasswordNotRecentlyUsed(user, newPassword);

        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.updatePassword(newPasswordHash);
        userRepository.save(user);

        passwordHistoryRepository.save(PasswordHistory.builder()
                .user(user)
                .passwordHash(newPasswordHash)
                .build());

        sessionTokenService.revokeActiveRefreshTokens(user);
    }

    private void validatePasswordNotRecentlyUsed(User user, String newPassword) {
        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
        }

        List<PasswordHistory> recentHistories = passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user);
        for (PasswordHistory history : recentHistories != null ? recentHistories : Collections.<PasswordHistory>emptyList()) {
            if (passwordEncoder.matches(newPassword, history.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
            }
        }
    }

    private User getActivePasswordResetUser(String email, ErrorCode notFoundErrorCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(notFoundErrorCode));
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }
        return user;
    }

    private void sendPasswordResetEmail(User user, String recipientEmail, String rawToken, String subject, String body) {
        String hashedToken = tokenHashService.hashSha256(rawToken);
        LocalDateTime expiryDate = LocalDateTime.now().plusHours(1);
        Long tokenId = createPendingPasswordResetToken(user, hashedToken, expiryDate);

        try {
            emailService.sendEmail(recipientEmail, subject, body);
            activatePasswordResetToken(tokenId, user);
        } catch (RuntimeException e) {
            try {
                updatePasswordResetDeliveryStatus(tokenId, false);
            } catch (RuntimeException statusUpdateException) {
                e.addSuppressed(statusUpdateException);
            }
            throw e;
        }
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

    private void updatePasswordResetDeliveryStatus(Long tokenId, boolean sent) {
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

    private void activatePasswordResetToken(Long tokenId, User user) {
        try {
            transactionTemplate.executeWithoutResult(status -> {
                invalidatePreviousSentPasswordResetTokens(user, tokenId);
                passwordResetTokenRepository.findById(tokenId)
                        .ifPresent(passwordResetToken -> {
                            passwordResetToken.markSent();
                            passwordResetTokenRepository.save(passwordResetToken);
                        });
            });
        } catch (RuntimeException e) {
            recoverSentPasswordResetToken(tokenId, user);
        }
    }

    private void recoverSentPasswordResetToken(Long tokenId, User user) {
        transactionTemplate.executeWithoutResult(status -> {
            invalidatePreviousSentPasswordResetTokens(user, tokenId);
            passwordResetTokenRepository.findById(tokenId)
                    .ifPresent(passwordResetToken -> {
                        passwordResetToken.markSent();
                        passwordResetTokenRepository.save(passwordResetToken);
                    });
        });
    }

    private void invalidatePreviousSentPasswordResetTokens(User user, Long excludeTokenId) {
        passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .filter(PasswordResetToken::isSent)
                .filter(passwordResetToken -> !passwordResetToken.getIsUsed())
                .filter(passwordResetToken -> excludeTokenId == null
                        || !excludeTokenId.equals(passwordResetToken.getTokenId()))
                .forEach(PasswordResetToken::invalidate);
    }

    private Optional<PasswordResetToken> findLatestSentCompatiblePasswordResetToken(User user) {
        return passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .filter(PasswordResetToken::isSent)
                .findFirst();
    }
}
