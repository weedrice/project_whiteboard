package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserKeywordSubscriptionRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private UserKeywordSubscriptionRepository repository;

    @Test
    void insertIgnore_isIdempotentForSameUserAndKeyword() {
        User user = User.builder()
                .loginId("keyword-user")
                .email("keyword-user@test.com")
                .password("password")
                .displayName("keyword user")
                .build();
        entityManager.persistAndFlush(user);
        entityManager.clear();

        assertThat(repository.insertIgnore(user.getUserId(), "spring")).isEqualTo(1);
        assertThat(repository.insertIgnore(user.getUserId(), "spring")).isZero();

        assertThat(repository.countByUser_UserId(user.getUserId())).isEqualTo(1);
        assertThat(repository.findByUser_UserIdAndKeyword(user.getUserId(), "spring")).isPresent();
    }
}
