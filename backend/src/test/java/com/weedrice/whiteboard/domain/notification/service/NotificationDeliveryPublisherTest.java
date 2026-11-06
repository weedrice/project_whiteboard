package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class NotificationDeliveryPublisherTest {
    private final NotificationStreamPublisher streamPublisher = mock(NotificationStreamPublisher.class);
    private final NotificationRepository notificationRepository = mock(NotificationRepository.class);
    private final UserBlockRepository userBlockRepository = mock(UserBlockRepository.class);
    private final ContentAuthorVisibilityRepository authorVisibilityRepository = mock(ContentAuthorVisibilityRepository.class);
    private final NotificationDeliveryPublisher publisher = new NotificationDeliveryPublisher(
            streamPublisher,
            new NotificationQueryService(notificationRepository,
                    notifications -> Map.of(7L, "/board/free/post/10"),
                    new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository)));
    private User receiver;
    private User actor;
    private Notification notification;

    @BeforeEach
    void setUp() {
        receiver = user(1L, "Receiver");
        actor = user(2L, "Current Name");
        notification = notification(actor);
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(notification));
        when(authorVisibilityRepository.findVisibleUserIds(List.of(2L))).thenReturn(Set.of(2L));
    }

    @Test
    void realtimeDeliveryMasksActorWhenBlockExistsAtPublishTime() {
        when(userBlockRepository.existsEitherDirection(1L, 2L)).thenReturn(true);

        publisher.publishAfterCommit(1L, notification);

        NotificationResponse.NotificationSummary summary = published();
        assertThat(summary.getActor().getUserId()).isNull();
        assertThat(summary.getActor().getDisplayName()).isEmpty();
        assertThat(summary.getMessage()).doesNotContain("Current Name", "Stored Name");
        assertThat(summary.getTargetUrl()).isEqualTo("/board/free/post/10");
    }

    @Test
    void realtimeDeliveryMasksActorWhenBannedAfterEventWasCreated() {
        when(authorVisibilityRepository.findVisibleUserIds(List.of(2L))).thenReturn(Set.of());

        publisher.publishAfterCommit(1L, notification);

        NotificationResponse.NotificationSummary summary = published();
        assertThat(summary.getActor().getUserId()).isNull();
        assertThat(summary.getMessage()).isEqualTo(" /  commented");
        assertThat(summary.getMessageParams()).containsExactly("", "Post title");
    }

    @Test
    void afterCommitReloadsWithdrawnActorInsteadOfUsingCachedActiveActor() {
        User withdrawnActor = user(2L, "Current Name");
        withdraw(withdrawnActor);
        Notification freshNotification = notification(withdrawnActor);
        TransactionSynchronizationManager.initSynchronization();
        try {
            publisher.publishAfterCommit(1L, notification);
            verifyNoInteractions(notificationRepository, streamPublisher);
            // The original event still contains its previously loaded ACTIVE entity.
            when(notificationRepository.findById(7L)).thenReturn(Optional.of(freshNotification));
            TransactionSynchronizationManager.getSynchronizations().forEach(sync -> sync.afterCommit());
        } finally {
            TransactionSynchronizationManager.clearSynchronization();
        }

        NotificationResponse.NotificationSummary summary = published();
        assertThat(summary.getActor().isWithdrawn()).isTrue();
        assertThat(summary.getActor().getUserId()).isEqualTo(2L);
        assertThat(summary.getActor().getDisplayName()).isEqualTo("탈퇴한 사용자");
        assertThat(summary.getMessage()).doesNotContain("Current Name", "Stored Name");
        assertThat(actor.getStatus()).isEqualTo(User.STATUS_ACTIVE);
        assertThat(notification.getContent()).isEqualTo("Stored Name / Current Name commented");
    }

    @Test
    void realtimeDeliveryRestoresPresentationFromReactivatedAccount() {
        withdraw(actor);
        User restoredActor = user(2L, "Restored Name");
        when(notificationRepository.findById(7L)).thenReturn(Optional.of(notification(restoredActor)));

        publisher.publishAfterCommit(1L, notification);

        NotificationResponse.NotificationSummary summary = published();
        assertThat(summary.getActor().isWithdrawn()).isFalse();
        assertThat(summary.getActor().getDisplayName()).isEqualTo("Restored Name");
        assertThat(summary.getMessageParams()).containsExactly("Stored Name", "Post title");
    }

    @Test
    void deletedOrMissingNotificationIsNotPublished() {
        when(notificationRepository.findById(7L)).thenReturn(Optional.empty());

        publisher.publishAfterCommit(1L, notification);

        verifyNoInteractions(streamPublisher);
    }

    @Test
    void inactiveOrDifferentReceiverDoesNotReceiveRealtimeNotification() {
        publisher.publishAfterCommit(3L, notification);
        withdraw(receiver);
        publisher.publishAfterCommit(1L, notification);

        verifyNoInteractions(streamPublisher);
    }

    private NotificationResponse.NotificationSummary published() {
        ArgumentCaptor<NotificationResponse.NotificationSummary> captor =
                ArgumentCaptor.forClass(NotificationResponse.NotificationSummary.class);
        verify(streamPublisher).publish(eq(1L), captor.capture());
        return captor.getValue();
    }

    private User user(Long id, String name) {
        User result = User.builder().displayName(name).build();
        ReflectionTestUtils.setField(result, "userId", id);
        return result;
    }

    private void withdraw(User target) {
        ReflectionTestUtils.setField(target, "status", "DELETED");
        ReflectionTestUtils.setField(target, "deletedAt", LocalDateTime.now());
    }

    private Notification notification(User currentActor) {
        Notification result = Notification.builder()
                .user(receiver)
                .actor(currentActor)
                .notificationType(NotificationType.COMMENT)
                .sourceType("POST")
                .sourceId(10L)
                .content("Stored Name / Current Name commented")
                .messageKey("notification.comment.created")
                .messageParams("[\"Stored Name\",\"Post title\"]")
                .build();
        ReflectionTestUtils.setField(result, "notificationId", 7L);
        return result;
    }
}
