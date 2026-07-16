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
import org.springframework.util.StringUtils;

import java.util.Objects;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SignupService {

    private static final String POINT_SIGNUP_BONUS_CONFIG_KEY = "POINT_SIGNUP_BONUS";
    private static final String POINT_SIGNUP_BONUS_DESCRIPTION = "회원가입 축하 포인트";
    private static final int DEFAULT_SIGNUP_BONUS = 500;

    private final UserRepository userRepository;
    private final PointService pointService;
    private final UserSettingsRepository userSettingsRepository;
    private final SocialAccountLinkService socialAccountLinkService;
    private final VerificationCodeService verificationCodeService;
    private final GlobalConfigService globalConfigService;
    private final EntityManager entityManager;
    private final RefreshTokenLifecycleService refreshTokenLifecycleService;
    private final UserPrivilegeCleanupService userPrivilegeCleanupService;
    private final PasswordHistoryPolicy passwordHistoryPolicy;
    private final AuthAccountEligibilityPolicy authAccountEligibilityPolicy;
    private final AccountUniquenessPolicy accountUniquenessPolicy;
    private final OAuthSignupTicketService oAuthSignupTicketService;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        OAuthSignupTicketService.OAuthSignupTicket oauthTicket = resolveOAuthTicket(request);
        String normalizedEmail = AuthEmailNormalizer.normalize(request.getEmail());
        validateOAuthTicketEmail(oauthTicket, normalizedEmail);

        var reregisterableUser = accountUniquenessPolicy.findReregisterableSignupUser(normalizedEmail);
        if (reregisterableUser.isPresent()) {
            return reregister(reregisterableUser.get(), request, normalizedEmail);
        }

        accountUniquenessPolicy.validateLoginIdAvailable(request.getLoginId());

        verificationCodeService.consumeVerificationTicket(
                normalizedEmail,
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());

        String passwordHash = passwordHistoryPolicy.encode(request.getPassword());
        User user = User.builder()
                .loginId(request.getLoginId())
                .password(passwordHash)
                .email(normalizedEmail)
                .displayName(request.getDisplayName())
                .build();
        user.verifyEmail();
        User savedUser = saveSignupUser(user, request, normalizedEmail);
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
            pointService.addPoint(
                    savedUser.getUserId(),
                    signupBonus,
                    POINT_SIGNUP_BONUS_DESCRIPTION,
                    savedUser.getUserId(),
                    "USER");
        }

        saveSocialAccountIfPresent(savedUser, request, oauthTicket);

        return SignupResponse.builder()
                .userId(savedUser.getUserId())
                .loginId(savedUser.getLoginId())
                .email(savedUser.getEmail())
                .displayName(savedUser.getDisplayName())
                .build();
    }

    @Transactional
    public SignupResponse reregister(User existingUser, SignupRequest request) {
        String normalizedEmail = AuthEmailNormalizer.normalize(request.getEmail());
        return reregister(existingUser, request, normalizedEmail);
    }

    private SignupResponse reregister(User existingUser, SignupRequest request, String normalizedEmail) {
        OAuthSignupTicketService.OAuthSignupTicket oauthTicket = resolveOAuthTicket(request);
        validateOAuthTicketEmail(oauthTicket, normalizedEmail);

        if (!Objects.equals(existingUser.getLoginId(), request.getLoginId())) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        verificationCodeService.consumeVerificationTicket(
                normalizedEmail,
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());

        passwordHistoryPolicy.validateNotRecentlyUsed(existingUser, request.getPassword());
        String passwordHash = passwordHistoryPolicy.encode(request.getPassword());
        refreshTokenLifecycleService.revokeActiveRefreshTokens(existingUser);
        userPrivilegeCleanupService.removeOperationalPrivileges(existingUser);
        existingUser.activate();
        existingUser.updatePassword(passwordHash);
        existingUser.updateDisplayName(request.getDisplayName());
        existingUser.updateEmail(normalizedEmail);
        existingUser.verifyEmail();
        userRepository.save(existingUser);
        passwordHistoryPolicy.record(existingUser, passwordHash);

        saveSocialAccountIfPresent(existingUser, request, oauthTicket);

        return SignupResponse.builder()
                .userId(existingUser.getUserId())
                .loginId(existingUser.getLoginId())
                .email(existingUser.getEmail())
                .displayName(existingUser.getDisplayName())
                .build();
    }

    public ReregisterCheckResponse checkEmailForReregister(String email) {
        AuthEmailNormalizer.normalize(email);
        return ReregisterCheckResponse.builder().canReregister(false).build();
    }

    @Transactional
    public FindIdResponse findLoginId(String email, String verificationTicket) {
        String normalizedEmail = AuthEmailNormalizer.normalize(email);
        verificationCodeService.validateVerificationTicket(
                normalizedEmail,
                VerificationPurpose.FIND_ID,
                verificationTicket);

        User user = userRepository.findByEmail(normalizedEmail)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        authAccountEligibilityPolicy.validateUsableAccount(user);
        verificationCodeService.consumeValidatedVerificationTicket(
                normalizedEmail,
                VerificationPurpose.FIND_ID,
                verificationTicket);
        return new FindIdResponse(user.getLoginId());
    }

    private User saveSignupUser(User user, SignupRequest request, String normalizedEmail) {
        try {
            return userRepository.saveAndFlush(user);
        } catch (DataIntegrityViolationException ex) {
            entityManager.clear();
            throw resolveSignupConflict(request, normalizedEmail, ex);
        }
    }

    private RuntimeException resolveSignupConflict(SignupRequest request, String normalizedEmail,
            DataIntegrityViolationException ex) {
        return accountUniquenessPolicy.resolveSignupConflict(normalizedEmail, request.getLoginId(), ex);
    }

    private OAuthSignupTicketService.OAuthSignupTicket resolveOAuthTicket(SignupRequest request) {
        if (!StringUtils.hasText(request.getOauthRegistrationTicket())) {
            return null;
        }
        return oAuthSignupTicketService.consume(request.getOauthRegistrationTicket());
    }

    private void validateOAuthTicketEmail(OAuthSignupTicketService.OAuthSignupTicket ticket, String normalizedEmail) {
        if (ticket == null) {
            return;
        }
        String ticketEmail = AuthEmailNormalizer.normalize(ticket.email());
        if (!Objects.equals(ticketEmail, normalizedEmail)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private void saveSocialAccountIfPresent(User user, SignupRequest request,
            OAuthSignupTicketService.OAuthSignupTicket oauthTicket) {
        String provider = oauthTicket != null ? oauthTicket.provider() : request.getProvider();
        String providerId = oauthTicket != null ? oauthTicket.providerId() : request.getProviderId();
        if (!StringUtils.hasText(provider) && !StringUtils.hasText(providerId)) {
            return;
        }

        socialAccountLinkService.linkSocialAccount(user, provider, providerId);
    }

}
