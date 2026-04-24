package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

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
        LocalDateTime now = LocalDateTime.now();
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

    private void persistRefreshToken(String tokenHash, LocalDateTime expiresAt, boolean revoked) {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(tokenHash)
                .ipAddress("127.0.0.1")
                .expiresAt(expiresAt)
                .build();
        if (revoked) {
            refreshToken.revoke();
        }
        entityManager.persist(refreshToken);
    }
}
