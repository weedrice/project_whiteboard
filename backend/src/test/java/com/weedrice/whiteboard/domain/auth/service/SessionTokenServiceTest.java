package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.Authentication;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SessionTokenServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 0, 0);
    private static final Clock CLOCK = Clock.fixed(
            Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private SanctionPolicyService sanctionPolicyService;
    @Mock private TransactionTemplate transactionTemplate;

    private final TokenHashService tokenHashService = new TokenHashService();
    private SessionTokenService service;
    private User user;

    @BeforeEach
    void setUp() {
        service = new SessionTokenService(
                userRepository,
                jwtTokenProvider,
                refreshTokenRepository,
                new LoginAccountEligibilityService(sanctionPolicyService),
                tokenHashService,
                transactionTemplate,
                CLOCK);
        user = User.builder()
                .loginId("session-user")
                .email("session-user@example.com")
                .password("password")
                .displayName("Session User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

    }

    @Test
    void logoutLocksUserBeforeRevokingEntireSessionFamily() {
        String rawToken = "logout-refresh-token";
        String tokenHash = tokenHashService.hashSha256(rawToken);
        UUID familyId = UUID.randomUUID();
        RefreshTokenRepository.RefreshTokenRenewalCandidate candidate =
                mock(RefreshTokenRepository.RefreshTokenRenewalCandidate.class);
        when(candidate.getUserId()).thenReturn(1L);
        when(candidate.getSessionFamilyId()).thenReturn(familyId);
        when(refreshTokenRepository.findRenewalCandidateByTokenHash(tokenHash)).thenReturn(Optional.of(candidate));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));

        service.logout(rawToken);

        InOrder order = inOrder(refreshTokenRepository, userRepository);
        order.verify(refreshTokenRepository).findRenewalCandidateByTokenHash(tokenHash);
        order.verify(userRepository).findByIdForUpdate(1L);
        order.verify(refreshTokenRepository).revokeTokenFamily(familyId);
    }

    @Test
    void replayingRotatedTokenRevokesFamilyAndReturnsInvalidRefreshToken() {
        String rawToken = "rotated-refresh-token";
        String tokenHash = tokenHashService.hashSha256(rawToken);
        UUID familyId = UUID.randomUUID();
        RefreshToken rotatedToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .sessionFamilyId(familyId)
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(NOW.plusDays(7))
                .build();
        rotatedToken.revoke();

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        });
        when(jwtTokenProvider.validateToken(rawToken)).thenReturn(true);
        RefreshTokenRepository.RefreshTokenRenewalCandidate renewalCandidate =
                mock(RefreshTokenRepository.RefreshTokenRenewalCandidate.class);
        when(renewalCandidate.getUserId()).thenReturn(1L);
        when(refreshTokenRepository.findRenewalCandidateByTokenHash(tokenHash))
                .thenReturn(Optional.of(renewalCandidate));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(tokenHash)).thenReturn(Optional.of(rotatedToken));

        BusinessException exception = assertThrows(BusinessException.class, () -> service.refresh(rawToken));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository).revokeTokenFamily(familyId);
        verify(jwtTokenProvider, never()).createAccessToken(any());
        verify(jwtTokenProvider, never()).createRefreshToken(any());
    }

    @Test
    void issueTokensUsesSecurityVersionFromLockedUserInsteadOfStalePrincipal() {
        ReflectionTestUtils.setField(user, "securityVersion", 4L);
        CustomUserDetails staleDetails = new CustomUserDetails(
                1L, user.getLoginId(), "", 3L, java.util.List.of());
        Authentication staleAuthentication = new UsernamePasswordAuthenticationToken(
                staleDetails, "", java.util.List.of());
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("refresh-token");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(60_000L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(120_000L);

        service.issueTokens(staleAuthentication, user, LoginClientMetadata.empty());

        ArgumentCaptor<Authentication> captor = ArgumentCaptor.forClass(Authentication.class);
        verify(jwtTokenProvider).createAccessToken(captor.capture());
        assertThat(((CustomUserDetails) captor.getValue().getPrincipal()).getSecurityVersion()).isEqualTo(4L);
    }

}
