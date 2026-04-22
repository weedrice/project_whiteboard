package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResponse;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionTokenService {

    private final UserRepository userRepository;
    private final UserPointRepository userPointRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final AuthenticationManagerBuilder authenticationManagerBuilder;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final SanctionService sanctionService;
    private final TokenHashService tokenHashService;

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

        if (!"ACTIVE".equals(user.getStatus()) || sanctionService.isUserBanned(user)) {
            throw new BusinessException(ErrorCode.LOGIN_FAILED);
        }

        String ipAddress = ClientUtils.getIp(httpServletRequest);
        String userAgent = httpServletRequest != null ? httpServletRequest.getHeader("User-Agent") : null;
        TokenResponse issuedTokens = issueTokens(authentication, user, httpServletRequest);

        LoginHistory loginHistory = LoginHistory.success(user, request.getLoginId(), ipAddress, userAgent);
        loginHistoryRepository.save(loginHistory);

        user.updateLastLogin();

        return LoginResult.builder()
                .accessToken(issuedTokens.getAccessToken())
                .refreshToken(issuedTokens.getRefreshToken())
                .expiresIn(issuedTokens.getExpiresIn())
                .user(LoginResponse.UserInfo.builder()
                        .userId(user.getUserId())
                        .loginId(user.getLoginId())
                        .displayName(user.getDisplayName())
                        .profileImageUrl(user.getProfileImageUrl())
                        .isEmailVerified(user.getIsEmailVerified())
                        .role(user.getIsSuperAdmin() ? Role.SUPER_ADMIN : Role.USER)
                        .points(userPointRepository.findById(user.getUserId())
                                .map(UserPoint::getCurrentPoint)
                                .orElse(0))
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

    private long getRefreshTokenValidityDays() {
        return jwtTokenProvider.getRefreshTokenValidityInMilliseconds() / (1000 * 60 * 60 * 24);
    }

    private void persistRefreshToken(User user, String refreshToken, String ipAddress, String userAgent) {
        String refreshTokenHash = tokenHashService.hashSha256(refreshToken);

        RefreshToken issuedRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .ipAddress(ipAddress != null ? ipAddress : "unknown")
                .deviceInfo(userAgent)
                .expiresAt(LocalDateTime.now().plusDays(getRefreshTokenValidityDays()))
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
}
