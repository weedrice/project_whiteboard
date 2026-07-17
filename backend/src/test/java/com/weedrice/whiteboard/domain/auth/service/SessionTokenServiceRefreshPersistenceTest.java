package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.TimeConfig;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@DataJpaTest
@Import({QuerydslConfig.class, TimeConfig.class, SessionTokenService.class, TokenHashService.class, LoginAccountEligibilityService.class})
@Transactional(propagation = Propagation.NOT_SUPPORTED)
class SessionTokenServiceRefreshPersistenceTest {

    @Autowired
    private SessionTokenService sessionTokenService;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    @Autowired
    private TokenHashService tokenHashService;

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private TransactionTemplate transactionTemplate;

    @MockitoBean
    private JwtTokenProvider jwtTokenProvider;

    @MockitoBean
    private SanctionPolicyService sanctionPolicyService;

    @Test
    void refresh_inactiveUserRevokesRefreshTokenBeforeReturningUserNotActive() {
        String oldRefreshToken = "old-refresh-token";
        String oldRefreshTokenHash = tokenHashService.hashSha256(oldRefreshToken);
        persistSuspendedUserRefreshToken(oldRefreshTokenHash);
        when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sessionTokenService.refresh(oldRefreshToken));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        RefreshToken reloadedToken = loadRefreshToken(oldRefreshTokenHash);
        assertThat(reloadedToken.getIsRevoked()).isTrue();
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    void refresh_deletedAtUserRevokesRefreshTokenBeforeReturningUserNotActive() {
        String oldRefreshToken = "deleted-at-refresh-token";
        String oldRefreshTokenHash = tokenHashService.hashSha256(oldRefreshToken);
        persistDeletedAtActiveUserRefreshToken(oldRefreshTokenHash);
        when(jwtTokenProvider.validateToken(oldRefreshToken)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sessionTokenService.refresh(oldRefreshToken));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        RefreshToken reloadedToken = loadRefreshToken(oldRefreshTokenHash);
        assertThat(reloadedToken.getIsRevoked()).isTrue();
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    void refresh_rotatedTokenReplayRevokesActiveSuccessorBeforeReturningInvalidToken() {
        String replayedRawToken = "replayed-refresh-token";
        String replayedHash = tokenHashService.hashSha256(replayedRawToken);
        String successorHash = tokenHashService.hashSha256("successor-refresh-token");
        UUID familyId = UUID.randomUUID();
        transactionTemplate.executeWithoutResult(ignored -> {
            User user = User.builder()
                    .loginId("refresh-replay-user")
                    .email("refresh-replay@test.com")
                    .password("password")
                    .displayName("Refresh Replay User")
                    .build();
            entityManager.persist(user);
            RefreshToken replayed = RefreshToken.builder()
                    .user(user)
                    .tokenHash(replayedHash)
                    .sessionFamilyId(familyId)
                    .ipAddress("127.0.0.1")
                    .deviceInfo("browser")
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();
            replayed.revoke();
            entityManager.persist(replayed);
            entityManager.persist(RefreshToken.builder()
                    .user(user)
                    .tokenHash(successorHash)
                    .sessionFamilyId(familyId)
                    .ipAddress("127.0.0.1")
                    .deviceInfo("browser")
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build());
            entityManager.flush();
            entityManager.clear();
        });
        when(jwtTokenProvider.validateToken(replayedRawToken)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> sessionTokenService.refresh(replayedRawToken));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        assertThat(loadRefreshToken(successorHash).getIsRevoked()).isTrue();
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    private void persistSuspendedUserRefreshToken(String tokenHash) {
        transactionTemplate.executeWithoutResult(ignored -> {
            User user = User.builder()
                    .loginId("inactive-refresh-user")
                    .email("inactive-refresh@test.com")
                    .password("password")
                    .displayName("Inactive Refresh User")
                    .build();
            user.suspend();
            entityManager.persist(user);

            RefreshToken refreshToken = RefreshToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .ipAddress("127.0.0.1")
                    .deviceInfo("browser")
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();
            entityManager.persist(refreshToken);
            entityManager.flush();
            entityManager.clear();
        });
    }

    private void persistDeletedAtActiveUserRefreshToken(String tokenHash) {
        transactionTemplate.executeWithoutResult(ignored -> {
            User user = User.builder()
                    .loginId("deleted-at-refresh-user")
                    .email("deleted-at-refresh@test.com")
                    .password("password")
                    .displayName("Deleted At Refresh User")
                    .build();
            ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now().minusDays(1));
            entityManager.persist(user);

            RefreshToken refreshToken = RefreshToken.builder()
                    .user(user)
                    .tokenHash(tokenHash)
                    .ipAddress("127.0.0.1")
                    .deviceInfo("browser")
                    .expiresAt(LocalDateTime.now().plusDays(7))
                    .build();
            entityManager.persist(refreshToken);
            entityManager.flush();
            entityManager.clear();
        });
    }

    private RefreshToken loadRefreshToken(String tokenHash) {
        return transactionTemplate.execute(ignored -> refreshTokenRepository.findByTokenHash(tokenHash).orElseThrow());
    }
}
