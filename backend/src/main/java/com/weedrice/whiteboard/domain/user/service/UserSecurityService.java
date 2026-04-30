package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.service.EmailEligibilityService;
import com.weedrice.whiteboard.domain.auth.service.RefreshTokenLifecycleService;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class UserSecurityService {

    private final UserRepository userRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final VerificationCodeService verificationCodeService;
    private final EmailEligibilityService emailEligibilityService;
    private final EntityManager entityManager;
    private final UserWritableResolver userWritableResolver;

    @Transactional
    public void updatePassword(Long userId, String currentPassword, String newPassword) {
        User user = userWritableResolver.resolve(userId);

        if (!passwordEncoder.matches(currentPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.INVALID_CURRENT_PASSWORD);
        }

        if (passwordEncoder.matches(newPassword, user.getPassword())) {
            throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
        }

        List<PasswordHistory> passwordHistories = passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user);
        for (PasswordHistory history : passwordHistories) {
            if (passwordEncoder.matches(newPassword, history.getPasswordHash())) {
                throw new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED);
            }
        }

        String newPasswordHash = passwordEncoder.encode(newPassword);
        user.updatePassword(newPasswordHash);
        passwordHistoryRepository.save(PasswordHistory.builder()
                .user(user)
                .passwordHash(newPasswordHash)
                .build());

        refreshTokenLifecycleService.revokeActiveRefreshTokens(user);
    }

    @Transactional
    public void verifyAndChangeEmail(Long userId, String email, String verificationTicket) {
        User user = userWritableResolver.resolve(userId);

        if (!user.getEmail().equals(email)) {
            emailEligibilityService.validateChangeEmail(email, user);
        }

        verificationCodeService.validateVerificationTicket(
                email,
                VerificationPurpose.CHANGE_EMAIL,
                verificationTicket);

        if (!user.getEmail().equals(email)) {
            user.updateEmail(email);
        }
        user.verifyEmail();

        try {
            userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            entityManager.clear();
            if (userRepository.findByEmail(email)
                    .filter(other -> !other.getUserId().equals(user.getUserId()))
                    .isPresent()) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            throw ex;
        }
        verificationCodeService.consumeValidatedVerificationTicket(
                email,
                VerificationPurpose.CHANGE_EMAIL,
                verificationTicket);
    }

}
