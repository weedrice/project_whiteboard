package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.TokenResponse;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import com.weedrice.whiteboard.global.security.SecurityAuthorities;
import lombok.RequiredArgsConstructor;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.UUID;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class SessionTokenService {

    private final UserRepository userRepository;
    private final JwtTokenProvider jwtTokenProvider;
    private final RefreshTokenRepository refreshTokenRepository;
    private final LoginAccountEligibilityService loginAccountEligibilityService;
    private final TokenHashService tokenHashService;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    @Transactional
    public void logout(String token) {
        if (token == null) {
            return;
        }

        String refreshTokenHash = tokenHashService.hashSha256(token);
        RefreshTokenRepository.RefreshTokenRenewalCandidate candidate = refreshTokenRepository
                .findRenewalCandidateByTokenHash(refreshTokenHash)
                .orElse(null);
        if (candidate == null) {
            return;
        }
        if (userRepository.findByIdForUpdate(candidate.getUserId()).isEmpty()) {
            return;
        }
        refreshTokenRepository.revokeTokenFamily(candidate.getSessionFamilyId());
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

        if (Boolean.TRUE.equals(refreshToken.getIsRevoked())) {
            refreshTokenRepository.revokeTokenFamily(refreshToken.getSessionFamilyId());
            return RefreshTokenRefreshOutcome.failure(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        if (refreshToken.isExpiredAt(now())) {
            return RefreshTokenRefreshOutcome.failure(ErrorCode.EXPIRED_REFRESH_TOKEN);
        }

        refreshToken.revoke();
        refreshTokenRepository.save(refreshToken);

        if (!loginAccountEligibilityService.evaluate(user).isLoginAllowed()) {
            refreshTokenRepository.revokeTokenFamily(refreshToken.getSessionFamilyId());
            return RefreshTokenRefreshOutcome.failure(ErrorCode.USER_NOT_ACTIVE);
        }

        user.updateLastLogin(now());

        Authentication authentication = createRefreshAuthentication(user);
        return RefreshTokenRefreshOutcome.success(
                issueTokens(
                        authentication,
                        user,
                        new LoginClientMetadata(refreshToken.getIpAddress(), refreshToken.getDeviceInfo()),
                        refreshToken.getSessionFamilyId()));
    }

    private RefreshTokenRenewalContext loadRefreshTokenRenewalContext(String refreshTokenHash) {
        RefreshTokenRepository.RefreshTokenRenewalCandidate candidate =
                refreshTokenRepository.findRenewalCandidateByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        User user = userRepository.findByIdForUpdate(candidate.getUserId())
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        RefreshToken refreshToken = refreshTokenRepository.findByTokenHash(refreshTokenHash)
                .orElseThrow(() -> new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN));
        if (!Objects.equals(refreshToken.getUser().getUserId(), user.getUserId())
                || !Objects.equals(refreshToken.getTokenHash(), refreshTokenHash)) {
            throw new BusinessException(ErrorCode.INVALID_REFRESH_TOKEN);
        }
        return new RefreshTokenRenewalContext(refreshToken, user);
    }

    private Authentication createRefreshAuthentication(User user) {
        List<GrantedAuthority> authorityList = SecurityAuthorities.user(user.getIsSuperAdmin());
        return new UsernamePasswordAuthenticationToken(
                new CustomUserDetails(user.getUserId(), user.getLoginId(), "", true, true, true, true, authorityList),
                "",
                authorityList);
    }

    private Duration getRefreshTokenValidityDuration() {
        return Duration.ofMillis(jwtTokenProvider.getRefreshTokenValidityInMilliseconds());
    }

    private void persistRefreshToken(User user, String refreshToken, String ipAddress, String userAgent,
            UUID sessionFamilyId) {
        String refreshTokenHash = tokenHashService.hashSha256(refreshToken);

        RefreshToken issuedRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(refreshTokenHash)
                .sessionFamilyId(sessionFamilyId)
                .ipAddress(LoginClientMetadataNormalizer.normalizeIpAddress(ipAddress))
                .deviceInfo(LoginClientMetadataNormalizer.normalizeDeviceInfo(userAgent))
                .expiresAt(now().plus(getRefreshTokenValidityDuration()))
                .build();
        refreshTokenRepository.save(issuedRefreshToken);
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    @Transactional
    public TokenResponse issueTokens(Authentication authentication, User user, LoginClientMetadata metadata) {
        return issueTokens(authentication, user, metadata, UUID.randomUUID());
    }

    private TokenResponse issueTokens(Authentication authentication, User user, LoginClientMetadata metadata,
            UUID sessionFamilyId) {
        LoginClientMetadata resolvedMetadata = metadata != null ? metadata : LoginClientMetadata.empty();
        String accessToken = jwtTokenProvider.createAccessToken(authentication);
        String refreshToken = jwtTokenProvider.createRefreshToken(authentication);

        persistRefreshToken(
                user,
                refreshToken,
                resolvedMetadata.ipAddress(),
                resolvedMetadata.userAgent(),
                sessionFamilyId);

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
