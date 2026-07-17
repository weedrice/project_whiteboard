package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.service.PasswordHistoryPolicy;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.MessageSource;
import org.springframework.context.i18n.LocaleContextHolder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Objects;
import java.util.Locale;
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
    private final AuthAccountEligibilityPolicy authAccountEligibilityPolicy;
    private final Clock clock;
    private final MessageSource messageSource;
    private final UserSettingsRepository userSettingsRepository;

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
                command.verificationEmail(),
                command.verificationTicket(),
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
                command.verificationEmail(),
                command.verificationTicket(),
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
            Long tokenId = passwordResetTokenOrchestrationService
                    .createPendingPasswordResetTokenForCurrentTransaction(user, rawToken);
            Locale locale = resolveUserLocale(user.getUserId());
            String subject = resolveMessage("mail.password-reset.subject", null,
                    "[NoviIs] Password reset link", locale);
            String body = buildPasswordResetBody(user, rawToken, includeLoginId, locale);
            return new PasswordResetMailCommand(
                    user,
                    normalizedEmail,
                    verificationTicket,
                    user.getEmail(),
                    tokenId,
                    subject,
                    body);
        }));
    }

    private String buildPasswordResetBody(User user, String rawToken, boolean includeLoginId, Locale locale) {
        String resetLink = passwordResetFrontendUrl + rawToken;
        if (includeLoginId) {
            String fallback = "<h1>Password reset</h1><p>Registered ID for this email: <strong>{0}</strong></p>"
                    + "<p>Use the link below to reset your password.</p>"
                    + "<p><a href=\"{1}\">Reset password</a></p>";
            return resolveMessage("mail.password-reset.body-with-login-id",
                    new Object[]{user.getLoginId(), resetLink}, fallback, locale);
        }
        String fallback = "<h1>Password reset</h1><p>Use the link below to reset your password.</p>"
                + "<p><a href=\"{0}\">{0}</a></p>";
        return resolveMessage("mail.password-reset.body", new Object[]{resetLink}, fallback, locale);
    }

    private Locale resolveUserLocale(Long userId) {
        if (userId == null) {
            return LocaleContextHolder.getLocale();
        }
        return userSettingsRepository.findSettingsReadByUserId(userId)
                .map(UserSettingsRepository.SettingsReadProjection::getLanguage)
                .filter("en"::equalsIgnoreCase)
                .map(ignored -> Locale.ENGLISH)
                .orElse(Locale.KOREAN);
    }

    private String resolveMessage(String key, Object[] args, String fallback, Locale locale) {
        return messageSource.getMessage(key, args, fallback, locale);
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

        if (passwordResetToken.isExpiredAt(LocalDateTime.now(clock))) {
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

        User user = getUsablePasswordResetUserForUpdate(normalizedEmail, ErrorCode.USER_NOT_FOUND);

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
        user.advanceSecurityVersion();
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

    private User getUsablePasswordResetUserForUpdate(String email, ErrorCode notFoundErrorCode) {
        User lockedUser = userRepository.findByEmailForUpdate(email)
                .orElseThrow(() -> new BusinessException(notFoundErrorCode));
        validateUsablePasswordResetUser(lockedUser);
        return lockedUser;
    }

    private void validateUsablePasswordResetUser(User user) {
        authAccountEligibilityPolicy.validateUsableAccount(user);
    }

    private record PasswordResetMailCommand(
            User user,
            String verificationEmail,
            String verificationTicket,
            String recipientEmail,
            Long tokenId,
            String subject,
            String body) {
    }
}
