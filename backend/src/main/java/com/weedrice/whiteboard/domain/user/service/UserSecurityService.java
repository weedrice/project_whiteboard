package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.service.AccountUniquenessPolicy;
import com.weedrice.whiteboard.domain.auth.service.AuthEmailNormalizer;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSecurityService {

    private final UserRepository userRepository;
    private final PasswordEncoder passwordEncoder;
    private final PasswordHistoryPolicy passwordHistoryPolicy;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final VerificationCodeService verificationCodeService;
    private final AccountUniquenessPolicy accountUniquenessPolicy;
    private final EntityManager entityManager;
    private final UserWritableResolver userWritableResolver;

    @Transactional
    public void updatePassword(Long userId, String currentPassword, String newPassword) {
        User user = userWritableResolver.resolveForUpdate(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        passwordHistoryPolicy.validateNotRecentlyUsed(user, newPassword);
        String newPasswordHash = passwordHistoryPolicy.encode(newPassword);
        user.updatePassword(newPasswordHash);
        user.advanceSecurityVersion();
        passwordHistoryPolicy.record(user, newPasswordHash);

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);
    }

    @Transactional
    public void verifyAndChangeEmail(Long userId, String email, String verificationTicket) {
        User user = userWritableResolver.resolveForUpdate(userId);
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        String normalizedCurrentEmail = AuthEmailNormalizer.normalize(user.getEmail());

        if (!normalizedCurrentEmail.equals(normalizedEmail)) {
            accountUniquenessPolicy.validateChangeEmailAvailable(normalizedEmail, user);
        }

        verificationCodeService.validateVerificationTicket(
                normalizedEmail,
                VerificationPurpose.CHANGE_EMAIL,
                verificationTicket);

        if (!user.getEmail().equals(normalizedEmail)) {
            user.updateEmail(normalizedEmail);
        }
        user.verifyEmail();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            entityManager.clear();
            throw accountUniquenessPolicy.resolveChangeEmailConflict(normalizedEmail, user.getUserId(), ex);
        }
        verificationCodeService.consumeValidatedVerificationTicket(
                normalizedEmail,
                VerificationPurpose.CHANGE_EMAIL,
                verificationTicket);
    }

}
