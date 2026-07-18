package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.util.List;

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

    @Test
    void matchingTitle_treatsSqlWildcardCharactersAsLiterals() {
        User user = User.builder()
                .loginId("literal-keyword-user")
                .email("literal-keyword-user@test.com")
                .password("password")
                .displayName("literal keyword user")
                .build();
        entityManager.persist(user);
        persistSubscription(user, "%");
        persistSubscription(user, "_");
        persistSubscription(user, "!");
        persistSubscription(user, "spring");
        entityManager.flush();
        entityManager.clear();

        assertThat(keywords(repository.findMatchingTitle("SPRING release")))
                .containsExactly("spring");
        assertThat(keywords(repository.findMatchingTitle("discount 10% !")))
                .containsExactlyInAnyOrder("%", "!");
        assertThat(keywords(repository.findMatchingTitle("snake_case")))
                .containsExactly("_");
    }

    @Test
    void matchingTitleAfter_usesTheSameLiteralMatchingRule() {
        User user = User.builder()
                .loginId("paged-keyword-user")
                .email("paged-keyword-user@test.com")
                .password("password")
                .displayName("paged keyword user")
                .build();
        entityManager.persist(user);
        persistSubscription(user, "%");
        UserKeywordSubscription spring = persistSubscription(user, "spring");
        entityManager.flush();
        entityManager.clear();

        assertThat(keywords(repository.findMatchingTitleAfter(
                "Spring release",
                spring.getSubscriptionId() - 1,
                PageRequest.of(0, 10))))
                .containsExactly("spring");
    }

    private UserKeywordSubscription persistSubscription(User user, String keyword) {
        return entityManager.persist(UserKeywordSubscription.builder()
                .user(user)
                .keyword(keyword)
                .build());
    }

    private List<String> keywords(List<UserKeywordSubscription> subscriptions) {
        return subscriptions.stream()
                .map(UserKeywordSubscription::getKeyword)
                .toList();
    }
}
