package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import({QuerydslConfig.class, NotificationQueryService.class})
class NotificationDeliveryPresentationTransactionTest {
    @Autowired
    private EntityManager entityManager;
    @Autowired
    private PlatformTransactionManager transactionManager;
    @Autowired
    private NotificationQueryService queryService;
    @MockitoBean
    private NotificationTargetUrlResolver targetUrlResolver;
    @MockitoBean
    private NotificationActorVisibilityService actorVisibilityService;

    private Long actorId;
    private Long receiverId;
    private Long notificationId;

    @AfterEach
    void cleanCommittedFixtures() {
        newTransaction().executeWithoutResult(status -> {
            if (notificationId != null) {
                entityManager.createQuery("DELETE FROM Notification n WHERE n.notificationId = :id")
                        .setParameter("id", notificationId).executeUpdate();
            }
            if (actorId != null && receiverId != null) {
                entityManager.createQuery("DELETE FROM User u WHERE u.userId IN (:actorId, :receiverId)")
                        .setParameter("actorId", actorId).setParameter("receiverId", receiverId).executeUpdate();
            }
        });
    }

    @Test
    void deliveryUsesNewPersistenceContextForWithdrawalAndReactivation() {
        newTransaction().executeWithoutResult(status -> {
            User actor = user("delivery-actor", "Original Name");
            User receiver = user("delivery-receiver", "Receiver");
            entityManager.persist(actor);
            entityManager.persist(receiver);
            Notification notification = Notification.builder().user(receiver).actor(actor)
                    .notificationType(NotificationType.COMMENT).sourceType("POST").sourceId(10L)
                    .content("Original Name commented").build();
            entityManager.persist(notification);
            entityManager.flush();
            actorId = actor.getUserId();
            receiverId = receiver.getUserId();
            notificationId = notification.getNotificationId();
        });
        Notification cached = entityManager.find(Notification.class, notificationId);
        assertThat(cached.getActor().getStatus()).isEqualTo(User.STATUS_ACTIVE);

        newTransaction().executeWithoutResult(status ->
                entityManager.find(User.class, actorId).delete(LocalDateTime.now()));

        var withdrawn = queryService.getNotificationForDelivery(receiverId, notificationId).orElseThrow();
        assertThat(withdrawn.getActor().isWithdrawn()).isTrue();
        assertThat(withdrawn.getActor().getDisplayName()).isEqualTo("탈퇴한 사용자");
        assertThat(withdrawn.getMessage()).doesNotContain("Original Name");
        assertThat(cached.getActor().getStatus()).isEqualTo(User.STATUS_ACTIVE);

        newTransaction().executeWithoutResult(status -> entityManager.find(User.class, actorId).activate());

        var restored = queryService.getNotificationForDelivery(receiverId, notificationId).orElseThrow();
        assertThat(restored.getActor().isWithdrawn()).isFalse();
        assertThat(restored.getActor().getDisplayName()).isEqualTo("Original Name");
        assertThat(restored.getMessage()).isEqualTo("Original Name commented");
    }

    private TransactionTemplate newTransaction() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    private User user(String loginId, String displayName) {
        return User.builder().loginId(loginId).email(loginId + "@example.com")
                .password("password").displayName(displayName).build();
    }
}
