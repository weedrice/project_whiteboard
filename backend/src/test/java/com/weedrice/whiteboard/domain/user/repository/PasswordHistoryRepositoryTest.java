package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PasswordHistoryRepositoryTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private PasswordHistoryRepository passwordHistoryRepository;

    @Test
    void deleteOlderThanLatestFour_keepsOnlyFourNewestRowsForUser() {
        User user = User.builder()
                .loginId("password-history-user")
                .email("password-history@test.com")
                .displayName("Password History")
                .password("current")
                .build();
        entityManager.persist(user);
        IntStream.rangeClosed(1, 6).forEach(index -> entityManager.persist(
                PasswordHistory.builder().user(user).passwordHash("hash-" + index).build()));
        entityManager.flush();

        int deleted = passwordHistoryRepository.deleteOlderThanLatestFour(user.getUserId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(2);
        assertThat(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user))
                .extracting(PasswordHistory::getPasswordHash)
                .containsExactly("hash-6", "hash-5", "hash-4", "hash-3");
        assertThat(passwordHistoryRepository.count()).isEqualTo(4);
    }
}
