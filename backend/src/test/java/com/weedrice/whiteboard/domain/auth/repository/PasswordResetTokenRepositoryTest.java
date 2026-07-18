package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PasswordResetTokenRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PasswordResetTokenRepository passwordResetTokenRepository;

    @Test
    void invalidateAllUnusedTokens_invalidatesEveryStatusOnlyForTargetUser() {
        User user = persistUser("reset-token-user");
        User anotherUser = persistUser("reset-token-other-user");
        PasswordResetToken pending = persistToken(user, "a".repeat(64));
        PasswordResetToken sent = persistToken(user, "b".repeat(64));
        sent.markSent();
        PasswordResetToken failed = persistToken(user, "c".repeat(64));
        failed.markFailed();
        PasswordResetToken alreadyUsed = persistToken(user, "d".repeat(64));
        alreadyUsed.useToken();
        PasswordResetToken anotherUserToken = persistToken(anotherUser, "e".repeat(64));
        entityManager.flush();
        entityManager.clear();

        int invalidatedCount = passwordResetTokenRepository.invalidateAllUnusedTokens(user);

        assertThat(invalidatedCount).isEqualTo(3);
        assertThat(find(pending).getIsUsed()).isTrue();
        assertThat(find(sent).getIsUsed()).isTrue();
        assertThat(find(failed).getIsUsed()).isTrue();
        assertThat(find(alreadyUsed).getIsUsed()).isTrue();
        assertThat(find(anotherUserToken).getIsUsed()).isFalse();
    }

    private User persistUser(String loginId) {
        User user = User.builder()
                .loginId(loginId)
                .email(loginId + "@example.com")
                .password("password")
                .displayName(loginId)
                .build();
        entityManager.persist(user);
        return user;
    }

    private PasswordResetToken persistToken(User user, String token) {
        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .user(user)
                .token(token)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        entityManager.persist(passwordResetToken);
        return passwordResetToken;
    }

    private PasswordResetToken find(PasswordResetToken token) {
        return passwordResetTokenRepository.findById(token.getTokenId()).orElseThrow();
    }
}
