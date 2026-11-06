package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Map;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class NotificationDeliveryPublisherTest {

    @Test
    void realtimeDeliveryMasksActorWhenBlockExistsAtPublishTime() {
        NotificationStreamPublisher streamPublisher = mock(NotificationStreamPublisher.class);
        UserBlockRepository userBlockRepository = mock(UserBlockRepository.class);
        ContentAuthorVisibilityRepository authorVisibilityRepository = mock(ContentAuthorVisibilityRepository.class);
        NotificationActorVisibilityService actorVisibilityService =
                new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository);
        NotificationDeliveryPublisher publisher = new NotificationDeliveryPublisher(
                streamPublisher,
                notifications -> Map.of(7L, "/board/free/post/10"),
                actorVisibilityService);
        User receiver = User.builder().displayName("Receiver").build();
        ReflectionTestUtils.setField(receiver, "userId", 1L);
        User actor = User.builder().displayName("Blocked Actor").build();
        ReflectionTestUtils.setField(actor, "userId", 2L);
        Notification notification = Notification.builder()
                .user(receiver)
                .actor(actor)
                .notificationType(NotificationType.COMMENT)
                .sourceType("POST")
                .sourceId(10L)
                .content("Blocked Actor commented")
                .build();
        ReflectionTestUtils.setField(notification, "notificationId", 7L);
        when(userBlockRepository.existsEitherDirection(1L, 2L)).thenReturn(true);
        when(authorVisibilityRepository.findVisibleUserIds(List.of(2L))).thenReturn(Set.of(2L));

        publisher.publishAfterCommit(1L, notification);

        ArgumentCaptor<NotificationResponse.NotificationSummary> summaryCaptor =
                ArgumentCaptor.forClass(NotificationResponse.NotificationSummary.class);
        verify(streamPublisher).publish(org.mockito.ArgumentMatchers.eq(1L), summaryCaptor.capture());
        NotificationResponse.NotificationSummary summary = summaryCaptor.getValue();
        assertThat(summary.getActor().getUserId()).isNull();
        assertThat(summary.getActor().getDisplayName()).isEmpty();
        assertThat(summary.getMessage()).doesNotContain("Blocked Actor");
        assertThat(summary.getTargetUrl()).isEqualTo("/board/free/post/10");
    }

    @Test
    void realtimeDeliveryMasksActorWhenBannedAfterEventWasCreated() {
        NotificationStreamPublisher streamPublisher = mock(NotificationStreamPublisher.class);
        UserBlockRepository userBlockRepository = mock(UserBlockRepository.class);
        ContentAuthorVisibilityRepository authorVisibilityRepository = mock(ContentAuthorVisibilityRepository.class);
        NotificationDeliveryPublisher publisher = new NotificationDeliveryPublisher(
                streamPublisher,
                notifications -> Map.of(7L, "/board/free/post/10"),
                new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository));
        User actor = User.builder().displayName("Current Name").build();
        ReflectionTestUtils.setField(actor, "userId", 2L);
        Notification notification = Notification.builder()
                .actor(actor)
                .notificationType(NotificationType.COMMENT)
                .sourceType("POST")
                .sourceId(10L)
                .content("Stored Name / Current Name commented")
                .messageKey("notification.comment.created")
                .messageParams("[\"Stored Name\",\"Post title\"]")
                .build();
        ReflectionTestUtils.setField(notification, "notificationId", 7L);
        when(authorVisibilityRepository.findVisibleUserIds(List.of(2L))).thenReturn(Set.of());

        publisher.publishAfterCommit(1L, notification);

        ArgumentCaptor<NotificationResponse.NotificationSummary> captor =
                ArgumentCaptor.forClass(NotificationResponse.NotificationSummary.class);
        verify(streamPublisher).publish(org.mockito.ArgumentMatchers.eq(1L), captor.capture());
        assertThat(captor.getValue().getActor().getUserId()).isNull();
        assertThat(captor.getValue().getActor().getDisplayName()).isEmpty();
        assertThat(captor.getValue().getMessage()).isEqualTo(" /  commented");
        assertThat(captor.getValue().getMessageParams()).containsExactly("", "Post title");
        assertThat(captor.getValue().getTargetUrl()).isEqualTo("/board/free/post/10");
    }
}
