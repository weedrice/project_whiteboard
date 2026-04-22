package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
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
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final TokenHashService tokenHashService;
    private final PasswordResetTokenOrchestrationService passwordResetTokenOrchestrationService;

    @Value("${cloud.aws.password-reset.frontend-url}")
    private String passwordResetFrontendUrl;

    @Transactional
    public void sendPasswordResetLink(String email, String verificationTicket) {
        verificationCodeService.consumeVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);

        User user = getActivePasswordResetUser(email, ErrorCode.USER_NOT_FOUND);
        String rawToken = UUID.randomUUID().toString();
        String resetLink = passwordResetFrontendUrl + rawToken;
        String subject = "[noviIs] Password reset link";
        String body = "<h1>Password reset</h1>"
                + "<p>Use the link below to reset your password.</p>"
                + "<p><a href=\"" + resetLink + "\">" + resetLink + "</a></p>";

        passwordResetTokenOrchestrationService.sendPasswordResetEmail(
                user,
                user.getEmail(),
                rawToken,
                subject,
                body);
    }

    @Transactional
    public void sendPasswordResetLinkByEmail(String email, String verificationTicket) {
        verificationCodeService.consumeVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);

        User user = getActivePasswordResetUser(email, ErrorCode.USER_NOT_FOUND_BY_EMAIL);
        String rawToken = UUID.randomUUID().toString();
        String resetLink = passwordResetFrontendUrl + rawToken;
        String subject = "[noviIs] Password reset link";
        String body = "<h1>Password reset</h1>"
                + "<p>Registered ID for this email: <strong>" + user.getLoginId() + "</strong></p>"
                + "<p>Use the link below to reset your password.</p>"
                + "<p><a href=\"" + resetLink + "\">Reset password</a></p>";

        passwordResetTokenOrchestrationService.sendPasswordResetEmail(
                user,
                user.getEmail(),
                rawToken,
                subject,
                body);
    }

    @Transactional
    public void resetPasswordWithToken(String rawToken, String newPassword) {
        String hashedToken = tokenHashService.hashSha256(rawToken);
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByTokenForUpdate(hashedToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        if (!passwordResetToken.isSent()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }

        PasswordResetToken latestSentToken = passwordResetTokenOrchestrationService
                .findLatestSentCompatibleToken(passwordResetToken.getUser())
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
        passwordResetToken.useToken();
        applyPasswordReset(user, newPassword);
        passwordResetTokenRepository.save(passwordResetToken);
    }

    @Transactional
    public void resetPasswordByCode(String email, String verificationTicket, String newPassword) {
        verificationCodeService.consumeVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }

        applyPasswordReset(user, newPassword);
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

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);
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
}
