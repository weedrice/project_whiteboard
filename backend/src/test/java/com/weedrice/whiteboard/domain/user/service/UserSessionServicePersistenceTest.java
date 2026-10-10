package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.auth.service.LoginAccountEligibilityService;
import com.weedrice.whiteboard.domain.auth.service.SessionTokenService;
import com.weedrice.whiteboard.domain.auth.service.TokenHashService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.dto.UserSessionResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import com.weedrice.whiteboard.global.security.SessionAuthenticationToken;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.ValueSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({QuerydslConfig.class, UserSessionService.class, UserReadableResolver.class,
        SessionTokenService.class, TokenHashService.class, LoginAccountEligibilityService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class UserSessionServicePersistenceTest {

    private static final Instant NOW = Instant.parse("2026-01-01T00:00:00Z");

    @Autowired private UserSessionService userSessionService;
    @Autowired private SessionTokenService sessionTokenService;
    @Autowired private RefreshTokenRepository refreshTokenRepository;
    @Autowired private TokenHashService tokenHashService;
    @Autowired private TestEntityManager entityManager;
    @Autowired private TransactionTemplate transactionTemplate;

    @MockitoBean private Clock clock;
    @MockitoBean private JwtTokenProvider jwtTokenProvider;
    @MockitoBean private SanctionPolicyService sanctionPolicyService;
    @MockitoBean private NotificationAccessInvalidationService notificationAccessInvalidationService;

    @BeforeEach
    void setUpClock() {
        when(clock.instant()).thenReturn(NOW);
        when(clock.getZone()).thenReturn(ZoneOffset.UTC);
    }

    @ParameterizedTest
    @ValueSource(booleans = {false, true})
    void listedSessionRevokesRotatedFamilyEvenAfterPredecessorExpires(boolean predecessorExpired) {
        String suffix = UUID.randomUUID().toString();
        String oldRawToken = "old-refresh-" + suffix;
        String newRawToken = "new-refresh-" + suffix;
        UUID familyId = UUID.randomUUID();
        SessionFixture fixture = persistSessions(suffix, oldRawToken, familyId);
        SessionAuthenticationToken authentication = authentication(fixture.userId(), familyId);
        Long listedSessionId = userSessionService.getActiveSessions(fixture.userId(), authentication).stream()
                .filter(UserSessionResponse::isCurrent)
                .findFirst().orElseThrow().getSessionId();
        assertThat(listedSessionId).isEqualTo(fixture.oldTokenId());

        when(jwtTokenProvider.validateToken(oldRawToken)).thenReturn(true);
        when(jwtTokenProvider.createAccessToken(any(), any(UUID.class))).thenReturn("access-token");
        when(jwtTokenProvider.createRefreshToken(any())).thenReturn(newRawToken);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(Duration.ofDays(7).toMillis());

        sessionTokenService.refresh(oldRawToken);

        RefreshToken successor = transactionTemplate.execute(ignored -> refreshTokenRepository
                .findByTokenHash(tokenHashService.hashSha256(newRawToken)).orElseThrow());
        assertThat(successor.getTokenId()).isNotEqualTo(listedSessionId);
        assertThat(successor.getSessionFamilyId()).isEqualTo(familyId);
        assertThat(loadToken(listedSessionId).getIsRevoked()).isTrue();
        assertThat(successor.getIsRevoked()).isFalse();
        if (predecessorExpired) {
            when(clock.instant()).thenReturn(NOW.plus(Duration.ofDays(2)));
            assertThat(loadToken(listedSessionId).isExpiredAt(LocalDateTime.now(clock))).isTrue();
        }

        assertThat(userSessionService.revokeSession(fixture.userId(), listedSessionId, authentication)
                .currentSessionRevoked()).isTrue();

        // Reload in a different transaction to check committed family revocation.
        assertThat(loadToken(successor.getTokenId()).getIsRevoked()).isTrue();
        assertThat(loadToken(fixture.otherTokenId()).getIsRevoked()).isFalse();
        assertThat(assertThrows(BusinessException.class,
                () -> userSessionService.revokeSession(fixture.userId(), listedSessionId, authentication))
                .getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
        verify(notificationAccessInvalidationService).disconnectSessionFamilyAfterCommit(fixture.userId(), familyId);
    }

    private SessionFixture persistSessions(String suffix, String oldRawToken, UUID familyId) {
        return transactionTemplate.execute(ignored -> {
            User user = entityManager.persist(User.builder()
                    .loginId("revoke-" + suffix.substring(0, 8))
                    .email("revoke-" + suffix + "@test.com")
                    .password("unused")
                    .displayName("Session revoke")
                    .build());
            RefreshToken old = entityManager.persist(token(user, tokenHashService.hashSha256(oldRawToken),
                    familyId, LocalDateTime.now(clock).plusDays(1)));
            RefreshToken other = entityManager.persist(token(user, tokenHashService.hashSha256("other-" + suffix),
                    UUID.randomUUID(), LocalDateTime.now(clock).plusDays(7)));
            entityManager.flush();
            return new SessionFixture(user.getUserId(), old.getTokenId(), other.getTokenId());
        });
    }

    private RefreshToken loadToken(Long tokenId) {
        return transactionTemplate.execute(ignored -> refreshTokenRepository.findById(tokenId).orElseThrow());
    }

    private static RefreshToken token(User user, String tokenHash, UUID familyId, LocalDateTime expiresAt) {
        return RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .sessionFamilyId(familyId)
                .deviceInfo("browser")
                .ipAddress("127.0.0.1")
                .expiresAt(expiresAt)
                .build();
    }

    private static SessionAuthenticationToken authentication(Long userId, UUID familyId) {
        CustomUserDetails principal = new CustomUserDetails(userId, "user-" + userId, "unused", List.of());
        return new SessionAuthenticationToken(principal, principal.getAuthorities(), familyId);
    }

    private record SessionFixture(Long userId, Long oldTokenId, Long otherTokenId) {
    }
}
