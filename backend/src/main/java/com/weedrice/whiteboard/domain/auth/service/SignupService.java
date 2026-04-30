package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.FindIdResponse;
import com.weedrice.whiteboard.domain.auth.dto.ReregisterCheckResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.service.PasswordHistoryPolicy;
import com.weedrice.whiteboard.domain.user.service.UserPrivilegeCleanupService;
import com.weedrice.whiteboard.domain.user.service.SocialAccountLinkService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupService {

    private static final String POINT_SIGNUP_BONUS_CONFIG_KEY = "POINT_SIGNUP_BONUS";
    private static final int DEFAULT_SIGNUP_BONUS = 500;

    private final UserRepository userRepository;
    private final PointService pointService;
    private final UserSettingsRepository userSettingsRepository;
    private final SocialAccountLinkService socialAccountLinkService;
    private final VerificationCodeService verificationCodeService;
    private final EmailEligibilityService emailEligibilityService;
    private final GlobalConfigService globalConfigService;
    private final EntityManager entityManager;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final UserPrivilegeCleanupService userPrivilegeCleanupService;
    private final PasswordHistoryPolicy passwordHistoryPolicy;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        emailEligibilityService.validateSignupEmail(request.getEmail());

        var reregisterableUser = userRepository.findByEmail(request.getEmail())
                .filter(existingUser -> "DELETED".equals(existingUser.getStatus()));
        if (reregisterableUser.isPresent()) {
            return reregister(reregisterableUser.get(), request);
        }

        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        verificationCodeService.consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());

        String passwordHash = passwordHistoryPolicy.encode(request.getPassword());
        User user = User.builder()
                .loginId(request.getLoginId())
                .password(passwordHash)
                .email(request.getEmail())
                .displayName(request.getDisplayName())
                .build();
        user.verifyEmail();
        User savedUser = saveSignupUser(user, request);
        passwordHistoryPolicy.record(savedUser, passwordHash);

        UserSettings userSettings = UserSettings.builder()
                .user(savedUser)
                .build();
        userSettingsRepository.save(userSettings);

        String signupBonusConfig = globalConfigService.getConfig(POINT_SIGNUP_BONUS_CONFIG_KEY);
        int signupBonus = GlobalConfigService.parseIntConfigOrDefault(
                signupBonusConfig,
                DEFAULT_SIGNUP_BONUS,
                0);
        if (signupBonus > 0) {
            pointService.addPoint(savedUser.getUserId(), signupBonus, "회원가입 축하 포인트", savedUser.getUserId(), "USER");
        }

        saveSocialAccountIfPresent(savedUser, request);

        return SignupResponse.builder()
                .userId(savedUser.getUserId())
                .loginId(savedUser.getLoginId())
                .email(savedUser.getEmail())
                .displayName(savedUser.getDisplayName())
                .build();
    }

    @Transactional
    public SignupResponse reregister(User existingUser, SignupRequest request) {
        if (!Objects.equals(existingUser.getLoginId(), request.getLoginId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        verificationCodeService.consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());

        passwordHistoryPolicy.validateNotRecentlyUsed(existingUser, request.getPassword());
        String passwordHash = passwordHistoryPolicy.encode(request.getPassword());
        refreshTokenLifecycleService.revokeActiveRefreshTokens(existingUser);
        userPrivilegeCleanupService.removeOperationalPrivileges(existingUser);
        existingUser.activate();
        existingUser.updatePassword(passwordHash);
        existingUser.updateDisplayName(request.getDisplayName());
        existingUser.verifyEmail();
        userRepository.save(existingUser);
        passwordHistoryPolicy.record(existingUser, passwordHash);

        saveSocialAccountIfPresent(existingUser, request);

        return SignupResponse.builder()
                .userId(existingUser.getUserId())
                .loginId(existingUser.getLoginId())
                .email(existingUser.getEmail())
                .displayName(existingUser.getDisplayName())
                .build();
    }

    public ReregisterCheckResponse checkEmailForReregister(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> "DELETED".equals(user.getStatus()))
                .map(user -> ReregisterCheckResponse.builder()
                        .canReregister(true)
                        .maskedLoginId(maskLoginId(user.getLoginId()))
                        .build())
                .orElse(ReregisterCheckResponse.builder().canReregister(false).build());
    }

    @Transactional
    public FindIdResponse findLoginId(String email, String verificationTicket) {
        verificationCodeService.validateVerificationTicket(email, VerificationPurpose.FIND_ID, verificationTicket);

        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }
        verificationCodeService.consumeValidatedVerificationTicket(email, VerificationPurpose.FIND_ID, verificationTicket);
        return new FindIdResponse(user.getLoginId());
    }

    private User saveSignupUser(User user, SignupRequest request) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            entityManager.clear();
            throw resolveSignupConflict(request, ex);
        }
    }

    private RuntimeException resolveSignupConflict(SignupRequest request, DataIntegrityViolationException ex) {
        if (userRepository.findByEmail(request.getEmail()).isPresent()) {
            return new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }
        if (userRepository.existsByLoginId(request.getLoginId())) {
            return new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }
        return ex;
    }

    private void saveSocialAccountIfPresent(User user, SignupRequest request) {
        if (request.getProvider() == null || request.getProviderId() == null) {
            return;
        }

        socialAccountLinkService.linkSocialAccount(user, request.getProvider(), request.getProviderId());
    }

    private String maskLoginId(String loginId) {
        if (loginId == null || loginId.isEmpty()) {
            return "****";
        }
        if (loginId.length() <= 4) {
            return "****";
        }
        String start = loginId.substring(0, 2);
        String end = loginId.substring(loginId.length() - 2);
        return start + "****" + end;
    }
}
