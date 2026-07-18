package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class LoginHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private LoginHistoryRepository loginHistoryRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("login-history-user")
                .email("login-history-user@test.com")
                .password("password")
                .displayName("Login History User")
                .build();
        entityManager.persist(user);
    }

    @Test
    @DisplayName("최근 성공 로그인 조회는 생성 시각이 같으면 historyId 내림차순으로 정렬한다")
    void findTopByUserAndIsSuccessTrueOrderByCreatedAtDescHistoryIdDesc_ordersByHistoryIdWhenCreatedAtTies() {
        LoginHistory first = persistSuccessHistory("first-login");
        LoginHistory second = persistSuccessHistory("second-login");
        persistFailureHistory("failed-login");
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        Optional<LoginHistory> result = loginHistoryRepository
                .findTopByUserAndIsSuccessTrueOrderByCreatedAtDescHistoryIdDesc(user);

        assertThat(result)
                .isPresent()
                .get()
                .extracting(LoginHistory::getHistoryId)
                .isEqualTo(second.getHistoryId());
    }

    @Test
    @DisplayName("로그인 이력 정리는 오래된 행부터 배치 크기만큼만 삭제한다")
    void deleteCreatedBeforeBatch_deletesOldestRowsWithinBatchLimit() {
        LoginHistory oldest = persistSuccessHistory("oldest-login");
        LoginHistory older = persistFailureHistory("older-login");
        LoginHistory recent = persistSuccessHistory("recent-login");
        entityManager.flush();
        LocalDateTime cutoff = LocalDateTime.now().minusDays(30);
        updateCreatedAt(oldest, cutoff.minusDays(2));
        updateCreatedAt(older, cutoff.minusDays(1));
        updateCreatedAt(recent, cutoff.plusDays(1));
        entityManager.clear();

        int deleted = loginHistoryRepository.deleteCreatedBeforeBatch(cutoff, 1);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(loginHistoryRepository.findById(oldest.getHistoryId())).isEmpty();
        assertThat(loginHistoryRepository.findById(older.getHistoryId())).isPresent();
        assertThat(loginHistoryRepository.findById(recent.getHistoryId())).isPresent();
    }

    private LoginHistory persistSuccessHistory(String loginId) {
        LoginHistory history = LoginHistory.success(user, loginId, "127.0.0.1", "test-agent");
        entityManager.persist(history);
        return history;
    }

    private LoginHistory persistFailureHistory(String loginId) {
        LoginHistory history = LoginHistory.failure(loginId, "127.0.0.1", "test-agent", "BAD_CREDENTIALS");
        entityManager.persist(history);
        return history;
    }

    private void updateCreatedAt(LoginHistory history, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE login_histories SET created_at = :createdAt WHERE history_id = :historyId")
                .setParameter("createdAt", createdAt)
                .setParameter("historyId", history.getHistoryId())
                .executeUpdate();
    }
}
