package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.FindIdResponse;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.ReregisterCheckResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.HashSet;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserPointRepository userPointRepository;
    private final PointService pointService;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SocialAccountRepository socialAccountRepository;
    private final PasswordHistoryRepository passwordHistoryRepository;
    private final VerificationCodeService verificationCodeService;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final EmailService emailService;
    private final GlobalConfigService globalConfigService;
    private final TransactionTemplate transactionTemplate;

    @Value("${cloud.aws.password-reset.frontend-url}")
    private String passwordResetFrontendUrl;

    @Transactional
    public SignupResponse signup(SignupRequest request) {
        var existingUserOpt = userRepository.findByEmail(request.getEmail());

        if (existingUserOpt.isPresent()) {
            User existingUser = existingUserOpt.get();
            if ("ACTIVE".equals(existingUser.getStatus())) {
                throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
            }
            if ("DELETED".equals(existingUser.getStatus())) {
                return reregister(existingUser, request);
            }
            throw new BusinessException(ErrorCode.DUPLICATE_EMAIL);
        }

        if (userRepository.existsByLoginId(request.getLoginId())) {
            throw new BusinessException(ErrorCode.DUPLICATE_LOGIN_ID);
        }

        if (!verificationCodeService.isVerified(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        User user = User.builder()
                .loginId(request.getLoginId())
                .password(passwordEncoder.encode(request.getPassword()))
                .email(request.getEmail())
                .displayName(request.getDisplayName())
                .build();
        user.verifyEmail();
        User savedUser = userRepository.save(user);

        UserSettings userSettings = UserSettings.builder()
                .user(user)
                .build();
        userSettingsRepository.save(userSettings);

        String signupBonusStr = globalConfigService.getConfig("POINT_SIGNUP_BONUS");
        int signupBonus = signupBonusStr != null ? Integer.parseInt(signupBonusStr) : 500;
        pointService.addPoint(savedUser.getUserId(), signupBonus, "회원가입 축하 포인트", savedUser.getUserId(), "USER");

        if (request.getProvider() != null && request.getProviderId() != null) {
            SocialAccount socialAccount = SocialAccount.builder()
                    .user(savedUser)
                    .provider(request.getProvider())
                    .providerId(request.getProviderId())
                    .build();
            socialAccountRepository.save(socialAccount);
        }

        return SignupResponse.builder()
                .userId(savedUser.getUserId())
                .loginId(savedUser.getLoginId())
                .email(savedUser.getEmail())
                .displayName(savedUser.getDisplayName())
                .build();
    }

    private SignupResponse reregister(User existingUser, SignupRequest request) {
        if (!verificationCodeService.isVerified(request.getEmail())) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }

        existingUser.activate();
        existingUser.updatePassword(passwordEncoder.encode(request.getPassword()));
        existingUser.updateDisplayName(request.getDisplayName());
        existingUser.verifyEmail();
        userRepository.save(existingUser);

        if (request.getProvider() != null && request.getProviderId() != null) {
            SocialAccount socialAccount = SocialAccount.builder()
                    .user(existingUser)
                    .provider(request.getProvider())
                    .providerId(request.getProviderId())
                    .build();
            socialAccountRepository.save(socialAccount);
        }

        return SignupResponse.builder()
                .userId(existingUser.getUserId())
                .loginId(existingUser.getLoginId())
                .email(existingUser.getEmail())
                .displayName(existingUser.getDisplayName())
                .build();
    }

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpServletRequest) {
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                request.getLoginId(), request.getPassword());

        Authentication authentication = authenticationManagerBuilder.getObject()
                .authenticate(authenticationToken);
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        Long userId = userDetails.getUserId();
        if (userId == null) {
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);
        String refreshTokenHash = hashTokenSha256(refreshToken);

        String ipAddress = ClientUtils.getIp(httpServletRequest);
        String userAgent = httpServletRequest.getHeader("User-Agent");

        long refreshDays = jwtTokenProvider.getRefreshTokenValidityInMilliseconds() / (1000 * 60 * 60 * 24);
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .ipAddress(ipAddress)
                .deviceInfo(userAgent)
                .expiresAt(LocalDateTime.now().plusDays(refreshDays))
                .build();
        refreshTokenRepository.save(rt);

        LoginHistory loginHistory = LoginHistory.success(user, request.getLoginId(), ipAddress, userAgent);
        loginHistoryRepository.save(loginHistory);

        user.updateLastLogin();

        return LoginResult.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInMilliseconds())
                .user(com.weedrice.whiteboard.domain.auth.dto.LoginResponse.UserInfo.builder()
                        .userId(user.getUserId())
                        .loginId(user.getLoginId())
                        .displayName(user.getDisplayName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .isEmailVerified(user.getIsEmailVerified())
                        .role(user.getIsSuperAdmin() ? Role.SUPER_ADMIN : Role.USER)
                        .points(userPointRepository.findById(user.getUserId()).map(UserPoint::getCurrentPoint)
                                .orElse(0))
                        .build())
                .build();
    }

    @Transactional
    public void logout(String token) {
        if (token != null) {
            String refreshTokenHash = hashTokenSha256(token);
            refreshTokenRepository.findByTokenHash(refreshTokenHash)
                    .ifPresent(refreshToken -> {
                        refreshToken.revoke();
                        refreshTokenRepository.save(refreshToken);
                    });
        }
    }

    @Transactional
    public TokenResponse refresh(String oldRefreshToken) {
        if (!jwtTokenProvider.validateToken(oldRefreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String oldRefreshTokenHash = hashTokenSha256(oldRefreshToken);
        RefreshToken rt = refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!rt.isValid()) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        rt.revoke();
        refreshTokenRepository.save(rt);

        User user = rt.getUser();

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }

        user.updateLastLogin();

        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER));
        if (user.getIsSuperAdmin()) {
            authorities.add(new SimpleGrantedAuthority(Role.ROLE_SUPER_ADMIN));
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user.getUserId(), user.getLoginId(), "", true, true, true, true,
                        new ArrayList<>(authorities)),
                "",
                new ArrayList<>(authorities));

        String newAccessToken = jwtTokenProvider.createAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication);
        String newRefreshTokenHash = hashTokenSha256(newRefreshToken);

        RefreshToken newRt = RefreshToken.builder()
                .user(user)
                .tokenHash(newRefreshTokenHash)
                .ipAddress(rt.getIpAddress())
                .deviceInfo(rt.getDeviceInfo())
                .expiresAt(LocalDateTime.now()
                        .plusDays(jwtTokenProvider.getRefreshTokenValidityInMilliseconds() / (1000 * 60 * 60 * 24)))
                .build();
        refreshTokenRepository.save(newRt);

        return TokenResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInMilliseconds())
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

    private String hashTokenSha256(String token) {
        try {
            MessageDigest digest = MessageDigest.getInstance("SHA-256");
            byte[] encodedhash = digest.digest(token.getBytes());
            StringBuilder hexString = new StringBuilder(2 * encodedhash.length);
            for (byte b : encodedhash) {
                String hex = Integer.toHexString(0xff & b);
                if (hex.length() == 1) {
                    hexString.append('0');
                }
                hexString.append(hex);
            }
            return hexString.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR);
        }
    }

    public FindIdResponse findLoginId(String email) {
        if (!verificationCodeService.isVerified(email)) {
            throw new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED);
        }
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }
        return new FindIdResponse(user.getLoginId());
    }

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
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

    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
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
        String hashedToken = hashTokenSha256(rawToken);
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

        revokeActiveRefreshTokens(user);
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

    private void revokeActiveRefreshTokens(User user) {
        List<RefreshToken> activeTokens = refreshTokenRepository.findByUserAndIsRevoked(user, false);
        List<RefreshToken> tokensToRevoke = activeTokens != null ? activeTokens : Collections.emptyList();
        for (RefreshToken token : tokensToRevoke) {
            token.revoke();
        }
        if (!tokensToRevoke.isEmpty()) {
            refreshTokenRepository.saveAll(tokensToRevoke);
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
        String hashedToken = hashTokenSha256(rawToken);
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
                .filter(passwordResetToken -> excludeTokenId == null || !excludeTokenId.equals(passwordResetToken.getTokenId()))
                .forEach(PasswordResetToken::invalidate);
    }

    private Optional<PasswordResetToken> findLatestSentCompatiblePasswordResetToken(User user) {
        return passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user)
                .stream()
                .filter(PasswordResetToken::isSent)
                .findFirst();
    }
}
