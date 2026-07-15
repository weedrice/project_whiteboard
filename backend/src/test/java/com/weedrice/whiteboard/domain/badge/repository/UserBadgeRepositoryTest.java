package com.weedrice.whiteboard.domain.badge.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@Import(QuerydslConfig.class)
class UserBadgeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserBadgeRepository userBadgeRepository;

    @Test
    void insertIgnoreReturnsOneThenZeroAndKeepsTransactionUsableOnH2PostgreSqlMode() {
        User user = User.builder()
                .loginId("badge-repository-user")
                .email("badge-repository-user@example.com")
                .password("password")
                .displayName("badge-repository-user")
                .build();
        entityManager.persist(user);
        entityManager.flush();
        entityManager.getEntityManager().createNativeQuery("""
                INSERT INTO badges (
                    badge_code,
                    name,
                    description,
                    icon,
                    tier,
                    sort_order,
                    created_at,
                    modified_at
                ) VALUES (
                    'TEST_BADGE',
                    'Test badge',
                    'Test badge description',
                    'test-icon',
                    'BRONZE',
                    1,
                    CURRENT_TIMESTAMP,
                    CURRENT_TIMESTAMP
                )
                """).executeUpdate();

        LocalDateTime acquiredAt = LocalDateTime.of(2026, 1, 1, 0, 0);
        int inserted = userBadgeRepository.insertIgnore(user.getUserId(), "TEST_BADGE", acquiredAt);
        int duplicate = userBadgeRepository.insertIgnore(user.getUserId(), "TEST_BADGE", acquiredAt);
        entityManager.clear();

        assertThat(inserted).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(userBadgeRepository.findByUser_UserIdAndBadge_BadgeCode(
                user.getUserId(), "TEST_BADGE")).isPresent();
    }
}
