package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Duration;
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
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final SanctionService sanctionService;
    private final TokenHashService tokenHashService;
    private final TransactionTemplate transactionTemplate;

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

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public TokenResponse refresh(String oldRefreshToken) {
        if (!jwtTokenProvider.validateToken(oldRefreshToken)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }

        RefreshTokenRefreshOutcome outcome = transactionTemplate.execute(ignored -> refreshInTransaction(oldRefreshToken));
        if (outcome.errorCode() != null) {
            throw new BusinessException(outcome.errorCode());
        }
        return outcome.tokenResponse();
    }

    private RefreshTokenRefreshOutcome refreshInTransaction(String oldRefreshToken) {
        String oldRefreshTokenHash = tokenHashService.hashSha256(oldRefreshToken);
        RefreshTokenRenewalContext renewalContext = loadRefreshTokenRenewalContext(oldRefreshTokenHash);
        RefreshToken refreshToken = renewalContext.refreshToken();
        User user = renewalContext.user();

        if (!refreshToken.isValid()) {
            throw new BusinessException(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        if (!"ACTIVE".equals(user.getStatus()) || sanctionService.isUserBanned(user)) {
            return RefreshTokenRefreshOutcome.failure(ErrorCode.USER_NOT_ACTIVE);
        }

        user.updateLastLogin();

        Authentication authentication = createRefreshAuthentication(user);
        return RefreshTokenRefreshOutcome.success(
                issueTokens(authentication, user, refreshToken.getIpAddress(), refreshToken.getDeviceInfo()));
    }

    private RefreshTokenRenewalContext loadRefreshTokenRenewalContext(String refreshTokenHash) {
        Long userId = refreshTokenRepository.findUserIdByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        User user = userRepository.findByIdForUpdate(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        return new RefreshTokenRenewalContext(refreshToken, user);
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
                .ipAddress(LoginClientMetadataNormalizer.normalizeIpAddress(ipAddress))
                .deviceInfo(LoginClientMetadataNormalizer.normalizeDeviceInfo(userAgent))
                .expiresAt(LocalDateTime.now().plus(getRefreshTokenValidityDuration()))
                .build();
        refreshTokenRepository.save(issuedRefreshToken);
    }

    @Transactional
    public TokenResponse issueTokens(Authentication authentication, User user, String ipAddress, String userAgent) {
        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        persistRefreshToken(user, refreshToken, ipAddress, userAgent);

        return TokenResponse.builder()
                .accessToken(accessToken)
                .refreshToken(refreshToken)
                .expiresIn(jwtTokenProvider.getAccessTokenValidityInMilliseconds())
                .build();
    }

    private record RefreshTokenRenewalContext(RefreshToken refreshToken, User user) {
    }

    private record RefreshTokenRefreshOutcome(TokenResponse tokenResponse, ErrorCode errorCode) {
        private static RefreshTokenRefreshOutcome success(TokenResponse tokenResponse) {
            return new RefreshTokenRefreshOutcome(tokenResponse, null);
        }

        private static RefreshTokenRefreshOutcome failure(ErrorCode errorCode) {
            return new RefreshTokenRefreshOutcome(null, errorCode);
        }
    }
}
