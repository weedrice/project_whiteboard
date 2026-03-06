package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.*;
import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.PointHistoryRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.SocialAccount;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.global.email.EmailService;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID; // Import UUID

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AuthService {

    private final UserRepository userRepository;
    private final UserPointRepository userPointRepository;
    private final PointHistoryRepository pointHistoryRepository;
    private final PasswordEncoder passwordEncoder;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final SocialAccountRepository socialAccountRepository;
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
        user.verifyEmail(); // Set isEmailVerified = true
        User savedUser = userRepository.save(user);

        // 기본 세팅 정보 생성
        UserSettings userSettings = UserSettings.builder()
                .user(user)
                .build();
        userSettingsRepository.save(userSettings);

        // 포인트 정보 생성 (가입 축하금)
        String signupBonusStr = globalConfigService.getConfig("POINT_SIGNUP_BONUS");
        int signupBonus = signupBonusStr != null ? Integer.parseInt(signupBonusStr) : 500;

        UserPoint userPoint = UserPoint.builder().user(savedUser).build();
        userPoint.addPoint(signupBonus);
        userPointRepository.save(userPoint);

        pointHistoryRepository.save(PointHistory.builder()
                .user(savedUser)
                .type("EARN")
                .amount(signupBonus)
                .balanceAfter(signupBonus)
                .description("회원가입 축하 포인트")
                .relatedId(savedUser.getUserId())
                .build());

        // Save SocialAccount if provider info is present
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
    public LoginResponse login(LoginRequest request, HttpServletRequest httpServletRequest) {
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

        // 사용자 상태 검증 (SUSPENDED, DELETED 사용자는 로그인 불가 - 메시지는 로그인 실패와 동일하게 노출)
        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);
        String refreshTokenHash = hashTokenSha256(refreshToken);

        String ipAddress = ClientUtils.getIp(httpServletRequest);
        String userAgent = httpServletRequest.getHeader("User-Agent");

        // Refresh Token 저장 (만료일은 jwt.refresh-token.expiration 설정값 사용)
        long refreshDays = jwtTokenProvider.getRefreshTokenValidityInMilliseconds() / (1000 * 60 * 60 * 24);
        RefreshToken rt = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .ipAddress(ipAddress)
                .deviceInfo(userAgent)
                .expiresAt(LocalDateTime.now().plusDays(refreshDays))
                .build();
        refreshTokenRepository.save(rt);

        // 로그인 기록 저장
        LoginHistory loginHistory = LoginHistory.success(user, request.getLoginId(), ipAddress, userAgent);
        loginHistoryRepository.save(loginHistory);

        user.updateLastLogin(); // 마지막 로그인 시간 업데이트

        return LoginResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInMilliseconds())
                .user(LoginResponse.UserInfo.builder()
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
    public void logout(LogoutRequest request) {
        String token = request.getRefreshToken();
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
    public RefreshResponse refresh(RefreshRequest request) {
        String oldRefreshToken = request.getRefreshToken();

        // 1. JWT 자체의 유효성 검증 (서명, 만료일 등)
        if (!jwtTokenProvider.validateToken(oldRefreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        // 2. DB에서 refresh token 조회
        String oldRefreshTokenHash = hashTokenSha256(oldRefreshToken);
        RefreshToken rt = refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        // 3. DB의 상태 검증 (revoked, expired)
        if (!rt.isValid()) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        // Revoke the old refresh token
        rt.revoke();
        refreshTokenRepository.save(rt);

        User user = rt.getUser();

        if (!"ACTIVE".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }

        user.updateLastLogin(); // 마지막 로그인 시간 업데이트

        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER)); // 기본 부여
        if (user.getIsSuperAdmin()) {
            authorities.add(new SimpleGrantedAuthority(Role.ROLE_SUPER_ADMIN)); // 추가 부여
        }

        Authentication authentication = new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user.getUserId(), user.getLoginId(), "", true, true, true, true,
                        new ArrayList<>(authorities)),
                "",
                new ArrayList<>(authorities));

        // Generate new access and refresh tokens
        String newAccessToken = jwtTokenProvider.createAccessToken(authentication);
        String newRefreshToken = jwtTokenProvider.createRefreshToken(authentication);
        String newRefreshTokenHash = hashTokenSha256(newRefreshToken);

        // Save the new refresh token
        RefreshToken newRt = RefreshToken.builder()
                .user(user)
                .tokenHash(newRefreshTokenHash)
                .ipAddress(rt.getIpAddress()) // Keep original IP/device info
                .deviceInfo(rt.getDeviceInfo())
                .expiresAt(LocalDateTime.now()
                        .plusDays(jwtTokenProvider.getRefreshTokenValidityInMilliseconds() / (1000 * 60 * 60 * 24))) // Use
                                                                                                                     // provider's
                                                                                                                     // validity
                .build();
        refreshTokenRepository.save(newRt);

        return RefreshResponse.builder()
                .accessToken(newAccessToken)
                .refreshToken(newRefreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInMilliseconds())
                .build();
    }

    /**
     * 재가입 가능 여부 및 마스킹된 loginId 조회.
     * DELETED 계정인 경우에만 canReregister=true, maskedLoginId 반환.
     */
    public ReregisterCheckResponse checkEmailForReregister(String email) {
        return userRepository.findByEmail(email)
                .filter(user -> "DELETED".equals(user.getStatus()))
                .map(user -> ReregisterCheckResponse.builder()
                        .canReregister(true)
                        .maskedLoginId(maskLoginId(user.getLoginId()))
                        .build())
                .orElse(ReregisterCheckResponse.builder().canReregister(false).build());
    }

    /**
     * loginId 마스킹: 앞 2자 + **** + 뒤 2자. 4자 이하면 ****.
     */
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
            throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR); // Or a more specific error
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

        String rawToken = UUID.randomUUID().toString();

        transactionTemplate.executeWithoutResult(status -> {
            User user = userRepository.findByEmail(email)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if ("DELETED".equals(user.getStatus())) {
                throw new BusinessException(ErrorCode.USER_DELETED);
            }

            String hashedToken = hashTokenSha256(rawToken);
            LocalDateTime expiryDate = LocalDateTime.now().plusHours(1); // 1시간 유효

            PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                    .token(hashedToken)
                    .user(user)
                    .expiryDate(expiryDate)
                    .build();
            passwordResetTokenRepository.save(passwordResetToken);
        });

        String resetLink = passwordResetFrontendUrl + rawToken;
        String subject = "[noviIs] 비밀번호 재설정 링크";
        String body = "<h1>비밀번호 재설정</h1><p>아래 링크를 클릭하여 비밀번호를 재설정해주세요.</p><p><a href=\"" + resetLink + "\">" + resetLink
                + "</a></p>";

        emailService.sendEmail(email, subject, body);
    }

    /**
     * 이메일로 비밀번호 초기화 링크 발송.
     * is_email_verified와 관계없이 해당 이메일로 등록된 사용자에게 발송.
     */
    @Transactional(propagation = org.springframework.transaction.annotation.Propagation.NOT_SUPPORTED)
    public void sendPasswordResetLinkByEmail(String email) {
        User user = userRepository.findByEmail(email)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND_BY_EMAIL));

        if ("DELETED".equals(user.getStatus())) {
            throw new BusinessException(ErrorCode.USER_DELETED);
        }

        String rawToken = UUID.randomUUID().toString();

        transactionTemplate.executeWithoutResult(status -> {
            String hashedToken = hashTokenSha256(rawToken);
            LocalDateTime expiryDate = LocalDateTime.now().plusHours(1);

            PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                    .token(hashedToken)
                    .user(user)
                    .expiryDate(expiryDate)
                    .build();
            passwordResetTokenRepository.save(passwordResetToken);
        });

        String resetLink = passwordResetFrontendUrl + rawToken;
        String subject = "[noviIs] 비밀번호 재설정";
        String body = "<h1>비밀번호 재설정</h1>"
                + "<p>해당 이메일로 등록된 ID: <strong>" + user.getLoginId() + "</strong></p>"
                + "<p>아래 링크를 클릭하여 비밀번호를 재설정해주세요.</p>"
                + "<p><a href=\"" + resetLink + "\">비밀번호 재설정 링크</a></p>";

        emailService.sendEmail(user.getEmail(), subject, body);
    }

    @Transactional
    public void resetPasswordWithToken(String rawToken, String newPassword) {
        String hashedToken = hashTokenSha256(rawToken);
        PasswordResetToken passwordResetToken = passwordResetTokenRepository.findByToken(hashedToken)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_PASSWORD_RESET_TOKEN));

        if (passwordResetToken.isExpired()) {
            throw new BusinessException(ErrorCode.EXPIRED_PASSWORD_RESET_TOKEN);
        }
        if (passwordResetToken.getIsUsed()) {
            throw new BusinessException(ErrorCode.USED_PASSWORD_RESET_TOKEN);
        }

        User user = passwordResetToken.getUser();
        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user); // Save user with new password

        passwordResetToken.useToken();
        passwordResetTokenRepository.save(passwordResetToken);

        // Clear verification code after password reset
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

        user.updatePassword(passwordEncoder.encode(newPassword));
        userRepository.save(user);

        // Clear verification status to prevent reuse
        verificationCodeService.clearVerificationStatus(email);
    }
}
