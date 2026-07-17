package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatCode;

@DataJpaTest
@Import(QuerydslConfig.class)
class PushSubscriptionRepositoryTest {

    @Autowired
    private PushSubscriptionRepository repository;

    @Autowired
    private TestEntityManager entityManager;

    @Test
    void lockEndpointUsesTransactionScopedH2Fallback() {
        assertThatCode(() -> repository.lockEndpoint("https://push.example/endpoint"))
                .doesNotThrowAnyException();
    }

    @Test
    void conditionalCleanupPreservesSubscriptionUpdatedAfterSnapshot() {
        User user = User.builder()
                .loginId("push-cleanup-user")
                .email("push-cleanup@example.com")
                .password("password")
                .displayName("Push Cleanup User")
                .build();
        entityManager.persist(user);
        PushSubscription subscription = PushSubscription.builder()
                .user(user)
                .endpoint("https://push.example/reused")
                .p256dh("old-key")
                .auth("old-auth")
                .userAgent("browser")
                .build();
        entityManager.persistAndFlush(subscription);
        Long subscriptionId = subscription.getSubscriptionId();
        Long userId = user.getUserId();
        LocalDateTime staleModifiedAt = subscription.getModifiedAt();

        subscription.update(user, "new-key", "new-auth", "browser");
        repository.saveAndFlush(subscription);

        int staleDeleted = repository.deleteIfSnapshotMatches(
                subscriptionId,
                userId,
                "https://push.example/reused",
                "old-key",
                "old-auth",
                staleModifiedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(staleDeleted).isZero();
        PushSubscription current = repository.findById(subscriptionId).orElseThrow();
        int currentDeleted = repository.deleteIfSnapshotMatches(
                current.getSubscriptionId(),
                current.getUser().getUserId(),
                current.getEndpoint(),
                current.getP256dh(),
                current.getAuth(),
                current.getModifiedAt());
        entityManager.flush();
        entityManager.clear();

        assertThat(currentDeleted).isEqualTo(1);
        assertThat(repository.findById(subscriptionId)).isEmpty();
    }

    @Test
    void conditionalCleanupPreservesEndpointTransferredToAnotherUser() {
        User previousUser = persistUser("push-owner-a", "push-owner-a@example.com");
        User currentUser = persistUser("push-owner-b", "push-owner-b@example.com");
        PushSubscription subscription = PushSubscription.builder()
                .user(previousUser)
                .endpoint("https://push.example/transferred")
                .p256dh("same-key")
                .auth("same-auth")
                .userAgent("browser")
                .build();
        entityManager.persistAndFlush(subscription);
        Long subscriptionId = subscription.getSubscriptionId();
        Long previousUserId = previousUser.getUserId();
        LocalDateTime staleModifiedAt = subscription.getModifiedAt();

        subscription.update(currentUser, "same-key", "same-auth", "browser");
        repository.saveAndFlush(subscription);

        int deleted = repository.deleteIfSnapshotMatches(
                subscriptionId,
                previousUserId,
                "https://push.example/transferred",
                "same-key",
                "same-auth",
                staleModifiedAt);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isZero();
        assertThat(repository.findById(subscriptionId)).isPresent();
    }

    private User persistUser(String loginId, String email) {
        User user = User.builder()
                .loginId(loginId)
                .email(email)
                .password("password")
                .displayName(loginId)
                .build();
        entityManager.persist(user);
        return user;
    }
}
