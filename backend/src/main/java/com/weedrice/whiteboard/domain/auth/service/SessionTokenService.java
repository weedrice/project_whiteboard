package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResponse;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService.LoginAccountEligibility;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.CurrentUserSummaryAssembler;
import com.weedrice.whiteboard.global.common.util.ClientUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.AuthenticationException;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(readOnly = true)
public class SessionTokenService {

    private static final String FAILURE_REASON_AUTHENTICATION_FAILED = "AUTHENTICATION_FAILED";

    private final UserRepository userRepository;
    private final CurrentUserSummaryAssembler currentUserSummaryAssembler;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final SanctionService sanctionService;
    private final TokenHashService tokenHashService;
    private final LoginHistoryAuditService loginHistoryAuditService;
    private final LoginAccountEligibilityService loginAccountEligibilityService;

    @Transactional
    public LoginResult login(LoginRequest request, HttpServletRequest httpServletRequest) {
        String ipAddress = resolveIpAddress(httpServletRequest);
        String userAgent = resolveUserAgent(httpServletRequest);
        UsernamePasswordAuthenticationToken authenticationToken = new UsernamePasswordAuthenticationToken(
                request.getLoginId(), request.getPassword());

        Authentication authentication;
        try {
            authentication = authenticationManagerBuilder.getObject()
                    .authenticate(authenticationToken);
        } catch (AuthenticationException exception) {
            recordLoginFailure(request, ipAddress, userAgent, resolveAuthenticationFailureReason(exception));
            throw exception;
        }
        CustomUserDetails userDetails = (CustomUserDetails) authentication.getPrincipal();

        Long userId = userDetails.getUserId();
        if (userId == null) {
            recordLoginFailure(request, ipAddress, userAgent,
                    LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_FOUND);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }
        User user = userRepository.findById(userId)
                .orElse(null);
        if (user == null) {
            recordLoginFailure(request, ipAddress, userAgent,
                    LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_FOUND);
            throw new BusinessException(ErrorCode.USER_NOT_FOUND);
        }

        LoginAccountEligibility eligibility = loginAccountEligibilityService.evaluate(user);
        if (!eligibility.isLoginAllowed()) {
            recordLoginFailure(request, ipAddress, userAgent, eligibility.failureReason());
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        TokenResponse issuedTokens = issueTokens(authentication, user, ipAddress, userAgent);

        LoginHistory loginHistory = LoginHistory.success(user, request.getLoginId(), ipAddress, userAgent);
        loginHistoryRepository.save(loginHistory);

        user.updateLastLogin();
        CurrentUserSummaryAssembler.CurrentUserSummary userSummary = currentUserSummaryAssembler.assemble(user);

        return LoginResult.builder()
                .accessToken(issuedTokens.getAccessToken())
                .refreshToken(issuedTokens.getRefreshToken())
                .expiresIn(issuedTokens.getExpiresIn())
                .user(LoginResponse.UserInfo.builder()
                        .userId(userSummary.userId())
                        .loginId(userSummary.loginId())
                        .displayName(userSummary.displayName())
                        .profileImageUrl(userSummary.profileImageUrl())
                        .isEmailVerified(Boolean.TRUE.equals(userSummary.isEmailVerified()))
                        .role(userSummary.role())
                        .theme(userSummary.theme())
                        .points(userSummary.points())
                        .build())
                .build();
    }

    @Transactional
    public TokenResponse issueTokens(Authentication authentication, User user, HttpServletRequest httpServletRequest) {
        String ipAddress = ClientUtils.getIp(httpServletRequest);
        String userAgent = httpServletRequest != null ? httpServletRequest.getHeader("User-Agent") : null;
        return issueTokens(authentication, user, ipAddress, userAgent);
    }

    @Transactional
    public void logout(String token) {
        if (token == null) {
            return;
        }

        String refreshTokenHash = tokenHashService.hashSha256(token);
        refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .ifPresent(refreshToken -> {
                    refreshToken.revoke();
                    refreshTokenRepository.save(refreshToken);
                });
    }

    @Transactional
    public TokenResponse refresh(String oldRefreshToken) {
        if (!jwtTokenProvider.validateToken(oldRefreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        String oldRefreshTokenHash = tokenHashService.hashSha256(oldRefreshToken);
        Long userId = refreshTokenRepository.findUserIdByTokenHash(oldRefreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));

        if (!refreshToken.isValid()) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        User user = refreshToken.getUser();
        if (!"ACTIVE".equals(user.getStatus()) || sanctionService.isUserBanned(user)) {
            throw new BusinessException(ErrorCode.USER_NOT_ACTIVE);
        }

        user.updateLastLogin();

        Authentication authentication = createRefreshAuthentication(user);
        return issueTokens(authentication, user, refreshToken.getIpAddress(), refreshToken.getDeviceInfo());
    }

    private Authentication createRefreshAuthentication(User user) {
        Set<GrantedAuthority> authorities = new HashSet<>();
        authorities.add(new SimpleGrantedAuthority(Role.ROLE_USER));
        if (user.getIsSuperAdmin()) {
            authorities.add(new SimpleGrantedAuthority(Role.ROLE_SUPER_ADMIN));
        }

        List<GrantedAuthority> authorityList = new ArrayList<>(authorities);
        return new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user.getUserId(), user.getLoginId(), "", true, true, true, true, authorityList),
                "",
                authorityList);
    }

    private Duration getRefreshTokenValidityDuration() {
        return Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityInMilliseconds());
    }

    private void persistRefreshToken(User user, String refreshToken, String ipAddress, String userAgent) {
        String refreshTokenHash = tokenHashService.hashSha256(refreshToken);

        RefreshToken issuedRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .ipAddress(ipAddress != null ? ipAddress : "unknown")
                .deviceInfo(userAgent)
                .expiresAt(LocalDateTime.now().plus(getRefreshTokenValidityDuration()))
                .build();
        refreshTokenRepository.save(issuedRefreshToken);
    }

    private TokenResponse issueTokens(Authentication authentication, User user, String ipAddress, String userAgent) {
        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        persistRefreshToken(user, refreshToken, ipAddress, userAgent);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInMilliseconds())
                .build();
    }

    private void recordLoginFailure(
            LoginRequest request,
            String ipAddress,
            String userAgent,
            String failureReason) {
        try {
            loginHistoryAuditService.recordFailure(request.getLoginId(), ipAddress, userAgent, failureReason);
        } catch (RuntimeException exception) {
            log.warn("Failed to record login failure history", exception);
        }
    }

    private String resolveAuthenticationFailureReason(AuthenticationException exception) {
        if (exception instanceof DisabledException) {
            return LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_ACTIVE;
        }
        if (exception instanceof LockedException) {
            return LoginAccountEligibilityService.FAILURE_REASON_USER_BANNED;
        }
        return FAILURE_REASON_AUTHENTICATION_FAILED;
    }

    private String resolveIpAddress(HttpServletRequest httpServletRequest) {
        return httpServletRequest != null ? ClientUtils.getIp(httpServletRequest) : null;
    }

    private String resolveUserAgent(HttpServletRequest httpServletRequest) {
        return httpServletRequest != null ? httpServletRequest.getHeader("User-Agent") : null;
    }
}
