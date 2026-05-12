package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.PasswordHistoryPolicy;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PasswordResetService {

    private final UserRepository userRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final PasswordHistoryPolicy passwordHistoryPolicy;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final TokenHashService tokenHashService;
    private final PasswordResetTokenOrchestrationService passwordResetTokenOrchestrationService;
    private final TransactionTemplate transactionTemplate;

    @Value("${cloud.aws.password-reset.frontend-url}")
    private String passwordResetFrontendUrl;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPasswordResetLink(String email, String verificationTicket) {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetMailCommand command = preparePasswordResetMail(
                email,
                verificationTicket,
                rawToken,
                ErrorCode.USER_NOT_FOUND,
                false);
        passwordResetTokenOrchestrationService.sendPreparedPasswordResetEmail(
                command.user(),
                command.recipientEmail(),
                command.tokenId(),
                command.subject(),
                command.body());
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public void sendPasswordResetLinkByEmail(String email, String verificationTicket) {
        String rawToken = UUID.randomUUID().toString();
        PasswordResetMailCommand command = preparePasswordResetMail(
                email,
                verificationTicket,
                rawToken,
                ErrorCode.USER_NOT_FOUND_BY_EMAIL,
                true);
        passwordResetTokenOrchestrationService.sendPreparedPasswordResetEmail(
                command.user(),
                command.recipientEmail(),
                command.tokenId(),
                command.subject(),
                command.body());
    }

    private PasswordResetMailCommand preparePasswordResetMail(
            String email,
            String verificationTicket,
            String rawToken,
            ErrorCode notFoundErrorCode,
            boolean includeLoginId) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        return Objects.requireNonNull(transactionTemplate.execute(status -> {
            verificationCodeService.validateVerificationTicket(
                    normalizedEmail,
                    VerificationPurpose.PASSWORD_RESET,
                    verificationTicket);
            User user = getUsablePasswordResetUser(normalizedEmail, notFoundErrorCode);
            verificationCodeService.consumeValidatedVerificationTicket(
                    normalizedEmail,
                    VerificationPurpose.PASSWORD_RESET,
                    verificationTicket);
            Long tokenId = passwordResetTokenOrchestrationService
                    .createPendingPasswordResetTokenForCurrentTransaction(user, rawToken);
            String subject = "[noviIs] Password reset link";
            String body = buildPasswordResetBody(user, rawToken, includeLoginId);
            return new PasswordResetMailCommand(user, user.getEmail(), tokenId, subject, body);
        }));
    }

    private String buildPasswordResetBody(User user, String rawToken, boolean includeLoginId) {
        String resetLink = passwordResetFrontendUrl + rawToken;
        String loginIdLine = includeLoginId
                ? "<p>Registered ID for this email: <strong>" + user.getLoginId() + "</strong></p>"
                : "";
        String linkText = includeLoginId ? "Reset password" : resetLink;
        return "<h1>Password reset</h1>"
                + loginIdLine
                + "<p>Use the link below to reset your password.</p>"
                + "<p><a href=\"" + resetLink + "\">" + linkText + "</a></p>";
    }

    @Transactional
    public void resetPasswordWithToken(String rawToken, String newPassword) {
        String hashedToken = tokenHashService.hashSha256(rawToken);
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByTokenForUpdate(hashedToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));
        User user = userRepository.findByIdForUpdate(passwordResetToken.getUser().getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        validateUsablePasswordResetUser(user);

        if (!passwordResetToken.isSent()) {
            throw new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
        }

        PasswordResetToken latestSentToken = passwordResetTokenOrchestrationService
                .findLatestSentCompatibleToken(user)
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

        passwordResetToken.useToken();
        passwordResetTokenRepository.save(passwordResetToken);
        applyPasswordReset(user, newPassword);
    }

    @Transactional
    public void resetPasswordByCode(String email, String verificationTicket, String newPassword) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        verificationCodeService.validateVerificationTicket(
                normalizedEmail,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);

        User user = getUsablePasswordResetUser(normalizedEmail, ErrorCode.USER_NOT_FOUND);

        passwordHistoryPolicy.validateNotRecentlyUsed(user, newPassword);
        verificationCodeService.consumeValidatedVerificationTicket(
                normalizedEmail,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        applyPasswordResetAfterValidation(user, newPassword);
    }

    private void applyPasswordReset(User user, String newPassword) {
        passwordHistoryPolicy.validateNotRecentlyUsed(user, newPassword);
        applyPasswordResetAfterValidation(user, newPassword);
    }

    private void applyPasswordResetAfterValidation(User user, String newPassword) {
        String newPasswordHash = passwordHistoryPolicy.encode(newPassword);
        user.updatePassword(newPasswordHash);
        userRepository.save(user);

        passwordHistoryPolicy.record(user, newPasswordHash);

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);
    }

    private User getUsablePasswordResetUser(String email, ErrorCode notFoundErrorCode) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(notFoundErrorCode));
        validateUsablePasswordResetUser(user);
        return user;
    }

    private void validateUsablePasswordResetUser(User user) {
        if (User.STATUS_DELETED.equals(user.getStatus()) || user.getDeletedAt() != null) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }
        if (!user.isActiveAccount()) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }
    }

    private record PasswordResetMailCommand(
            User user,
            String recipientEmail,
            Long tokenId,
            String subject,
            String body) {
    }
}
