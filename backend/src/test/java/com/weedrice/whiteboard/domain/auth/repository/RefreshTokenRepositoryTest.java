package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.LockModeType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.jpa.repository.Lock;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class RefreshTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private RefreshTokenRepository refreshTokenRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("user")
                .email("user@test.com")
                .password("password")
                .displayName("User")
                .build();
        entityManager.persist(user);
    }

    @Test
    void findByUserAndIsRevokedAndExpiresAtGreaterThanEqual_returnsOnlyUnrevokedUnexpiredTokens() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        persistRefreshToken("active", now.plusHours(1), false);
        persistRefreshToken("boundary", now, false);
        persistRefreshToken("expired", now.minusMinutes(1), false);
        persistRefreshToken("revoked", now.plusHours(1), true);
        entityManager.flush();
        entityManager.clear();

        var tokens = refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, now);

        assertThat(tokens).extracting(RefreshToken::getTokenHash)
                .containsExactlyInAnyOrder("active", "boundary");
    }

    @Test
    void findRenewalCandidateByTokenHash_returnsTokenAndUserIds() {
        RefreshToken refreshToken = persistRefreshToken("active", LocalDateTime.now().plusHours(1), false);
        entityManager.flush();
        entityManager.clear();

        var candidate = refreshTokenRepository.findRenewalCandidateByTokenHash("active");

        assertThat(candidate).isPresent();
        assertThat(candidate.get().getTokenId()).isEqualTo(refreshToken.getTokenId());
        assertThat(candidate.get().getUserId()).isEqualTo(user.getUserId());
        assertThat(candidate.get().getSessionFamilyId()).isEqualTo(refreshToken.getSessionFamilyId());
    }

    @Test
    void findByTokenIdForUpdate_declaresPessimisticWriteLock() throws NoSuchMethodException {
        Method method = RefreshTokenRepository.class.getMethod("findByTokenIdForUpdate", Long.class);

        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void findByTokenHash_declaresPessimisticWriteLock() throws NoSuchMethodException {
        Method method = RefreshTokenRepository.class.getMethod("findByTokenHash", String.class);

        assertThat(method.getAnnotation(Lock.class).value()).isEqualTo(LockModeType.PESSIMISTIC_WRITE);
    }

    @Test
    void revokeActiveTokensByUserId_revokesOnlyActiveUnexpiredTokens() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        persistRefreshToken("active", now.plusHours(1), false);
        persistRefreshToken("boundary", now, false);
        persistRefreshToken("expired", now.minusMinutes(1), false);
        persistRefreshToken("revoked", now.plusHours(1), true);
        entityManager.flush();
        entityManager.clear();

        int updated = refreshTokenRepository.revokeActiveTokensByUserId(user.getUserId(), now);
        entityManager.flush();
        entityManager.clear();

        assertThat(updated).isEqualTo(2);
        assertThat(refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, now))
                .extracting(RefreshToken::getTokenHash)
                .isEmpty();
    }

    @Test
    void revokeTokenFamily_revokesEveryGenerationWithoutTouchingOtherFamilies() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID revokedFamily = UUID.randomUUID();
        UUID retainedFamily = UUID.randomUUID();
        persistRefreshToken("family-old", now.plusHours(1), false, revokedFamily);
        persistRefreshToken("family-current", now.plusHours(2), false, revokedFamily);
        persistRefreshToken("other-family", now.plusHours(1), false, retainedFamily);
        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.revokeTokenFamily(revokedFamily)).isEqualTo(2);
        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, now))
                .extracting(RefreshToken::getTokenHash)
                .containsExactly("other-family");
    }

    @Test
    void revokeActiveTokensByUserIdExceptFamily_preservesCurrentFamily() {
        LocalDateTime now = LocalDateTime.now().truncatedTo(ChronoUnit.SECONDS);
        UUID currentFamily = UUID.randomUUID();
        persistRefreshToken("current-family", now.plusHours(1), false, currentFamily);
        persistRefreshToken("other-family", now.plusHours(1), false, UUID.randomUUID());
        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.revokeActiveTokensByUserIdExceptFamily(
                user.getUserId(), currentFamily, now)).isEqualTo(1);
        entityManager.flush();
        entityManager.clear();

        assertThat(refreshTokenRepository.findByUserAndIsRevokedAndExpiresAtGreaterThanEqual(user, false, now))
                .extracting(RefreshToken::getTokenHash)
                .containsExactly("current-family");
    }

    private RefreshToken persistRefreshToken(String tokenHash, LocalDateTime expiresAt, boolean revoked) {
        return persistRefreshToken(tokenHash, expiresAt, revoked, UUID.randomUUID());
    }

    private RefreshToken persistRefreshToken(
            String tokenHash, LocalDateTime expiresAt, boolean revoked, UUID sessionFamilyId) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .sessionFamilyId(sessionFamilyId)
                .ipAddress("127.0.0.1")
                .expiresAt(expiresAt)
                .build();
        if (revoked) {
            refreshToken.revoke();
        }
        entityManager.persist(refreshToken);
        return refreshToken;
    }
}
