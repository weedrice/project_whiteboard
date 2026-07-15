package com.weedrice.whiteboard.domain.point.repository;

import com.weedrice.whiteboard.domain.point.entity.PointHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PointHistoryRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PointHistoryRepository pointHistoryRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("point-user")
                .email("point-user@test.com")
                .password("password")
                .displayName("Point User")
                .build();
        entityManager.persist(user);
    }

    @Test
    @DisplayName("포인트 이력 전체 조회는 생성 시각이 같으면 historyId 내림차순으로 정렬한다")
    void findByUser_UserIdOrderByCreatedAtDescHistoryIdDesc_ordersByHistoryIdWhenCreatedAtTies() {
        PointHistory first = persistHistory("EARN", 10, 10);
        PointHistory second = persistHistory("SPEND", -5, 5);
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        Page<PointHistory> result = pointHistoryRepository.findByUser_UserIdOrderByCreatedAtDescHistoryIdDesc(
                user.getUserId(),
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(PointHistory::getHistoryId)
                .containsExactly(second.getHistoryId(), first.getHistoryId());
    }

    @Test
    @DisplayName("포인트 이력 타입별 조회는 생성 시각이 같으면 historyId 내림차순으로 정렬한다")
    void findByUser_UserIdAndTypeOrderByCreatedAtDescHistoryIdDesc_ordersByHistoryIdWhenCreatedAtTies() {
        PointHistory first = persistHistory("EARN", 10, 10);
        PointHistory second = persistHistory("EARN", 20, 30);
        persistHistory("SPEND", -5, 25);
        LocalDateTime sameCreatedAt = LocalDateTime.now().minusMinutes(1);
        entityManager.flush();
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        Page<PointHistory> result = pointHistoryRepository.findByUser_UserIdAndTypeOrderByCreatedAtDescHistoryIdDesc(
                user.getUserId(),
                "EARN",
                PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(PointHistory::getHistoryId)
                .containsExactly(second.getHistoryId(), first.getHistoryId());
    }

    @Test
    @DisplayName("콘텐츠 생성 보상 회수 합계는 조건에 맞는 양수 적립 이력만 합산한다")
    void sumPositiveAmountByUserAndTypeAndRelatedTypeAndRelatedId_sumsOnlyMatchingPositiveEarnHistories() {
        User otherUser = User.builder()
                .loginId("other-point-user")
                .email("other-point-user@test.com")
                .password("password")
                .displayName("Other Point User")
                .build();
        entityManager.persist(otherUser);
        persistHistory(user, "EARN", 10, 10, "COMMENT", 100L);
        persistHistory(user, "EARN", 15, 25, "COMMENT", 100L);
        persistHistory(user, "EARN", -5, 20, "COMMENT", 100L);
        persistHistory(user, "SPEND", 7, 27, "COMMENT", 100L);
        persistHistory(user, "EARN", 30, 50, "POST", 100L);
        persistHistory(user, "EARN", 40, 65, "COMMENT", 101L);
        persistHistory(otherUser, "EARN", 50, 50, "COMMENT", 100L);
        entityManager.flush();
        entityManager.clear();

        long result = pointHistoryRepository.sumPositiveAmountByUserAndTypeAndRelatedTypeAndRelatedId(
                user,
                "EARN",
                "COMMENT",
                100L);

        assertThat(result).isEqualTo(25L);
    }

    @Test
    @DisplayName("콘텐츠 생성 보상 회수 합계는 양수 적립 이력이 없으면 0을 반환한다")
    void sumPositiveAmountByUserAndTypeAndRelatedTypeAndRelatedId_withoutPositiveEarnHistories_returnsZero() {
        persistHistory(user, "EARN", -5, 95, "POST", 100L);
        persistHistory(user, "SPEND", 10, 105, "POST", 100L);
        entityManager.flush();
        entityManager.clear();

        long result = pointHistoryRepository.sumPositiveAmountByUserAndTypeAndRelatedTypeAndRelatedId(
                user,
                "EARN",
                "POST",
                100L);

        assertThat(result).isZero();
    }

    private PointHistory persistHistory(String type, int amount, int balanceAfter) {
        return persistHistory(user, type, amount, balanceAfter, null, null);
    }

    private PointHistory persistHistory(
            User targetUser,
            String type,
            int amount,
            int balanceAfter,
            String relatedType,
            Long relatedId) {
        PointHistory history = PointHistory.builder()
                .user(targetUser)
                .type(type)
                .amount(amount)
                .balanceAfter(balanceAfter)
                .description("test")
                .relatedType(relatedType)
                .relatedId(relatedId)
                .build();
        entityManager.persist(history);
        return history;
    }

    private void updateCreatedAt(PointHistory history, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE point_histories SET created_at = :createdAt WHERE history_id = :historyId")
                .setParameter("createdAt", createdAt)
                .setParameter("historyId", history.getHistoryId())
                .executeUpdate();
    }
}
