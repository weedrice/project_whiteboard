package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.times;

class PushDeliveryJobEnqueuerTest {

    @Test
    void enqueuePersistsOneJobPerEnabledSubscriptionWithEventIdentity() {
        WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey("public-key");
        properties.setPrivateKey("private-key");
        properties.setSubject("mailto:ops@example.com");
        PushDispatchSnapshotReader reader = mock(PushDispatchSnapshotReader.class);
        PushDeliveryJobRepository repository = mock(PushDeliveryJobRepository.class);
        PushPayloadFactory payloadFactory = mock(PushPayloadFactory.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);
        PushDeliveryJobEnqueuer enqueuer = new PushDeliveryJobEnqueuer(
                properties, reader, repository, payloadFactory, clock);
        UUID eventId = UUID.randomUUID();
        NotificationEvent event = mock(NotificationEvent.class);
        Notification notification = mock(Notification.class);
        User receiver = mock(User.class);
        when(event.getEventId()).thenReturn(eventId);
        when(notification.getUser()).thenReturn(receiver);
        when(receiver.getUserId()).thenReturn(3L);
        when(notification.getNotificationId()).thenReturn(7L);
        when(notification.getContent()).thenReturn("content");
        when(notification.getNotificationType()).thenReturn(NotificationType.COMMENT);
        LocalDateTime modifiedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).minusMinutes(1);
        when(reader.loadEnabledSubscriptions(3L)).thenReturn(List.of(
                new PushSubscriptionSnapshot(11L, 3L, "https://push/1", "k1", "a1", modifiedAt),
                new PushSubscriptionSnapshot(12L, 3L, "https://push/2", "k2", "a2", modifiedAt)));
        when(payloadFactory.create(org.mockito.ArgumentMatchers.any())).thenReturn("payload");
        when(repository.insertIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true);

        assertThat(enqueuer.enqueue(event, notification)).isEqualTo(2);

        org.mockito.ArgumentCaptor<PushDeliveryJob> captor = org.mockito.ArgumentCaptor.forClass(PushDeliveryJob.class);
        verify(repository, times(2)).insertIfAbsent(captor.capture());
        assertThat(captor.getAllValues()).hasSize(2)
                .allSatisfy(job -> {
                    assertThat(job.getEventId()).isEqualTo(eventId);
                    assertThat(job.getStatus()).isEqualTo(PushDeliveryJob.Status.PENDING);
                    assertThat(job.getPayload()).isEqualTo("payload");
                });
    }

    @Test
    void enqueueCountsOnlyJobsActuallyInserted() {
        WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey("public-key");
        properties.setPrivateKey("private-key");
        properties.setSubject("mailto:ops@example.com");
        PushDispatchSnapshotReader reader = mock(PushDispatchSnapshotReader.class);
        PushDeliveryJobRepository repository = mock(PushDeliveryJobRepository.class);
        PushPayloadFactory payloadFactory = mock(PushPayloadFactory.class);
        Clock clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00Z"), ZoneOffset.UTC);
        PushDeliveryJobEnqueuer enqueuer = new PushDeliveryJobEnqueuer(
                properties, reader, repository, payloadFactory, clock);
        NotificationEvent event = mock(NotificationEvent.class);
        Notification notification = mock(Notification.class);
        User receiver = mock(User.class);
        when(event.getEventId()).thenReturn(UUID.randomUUID());
        when(notification.getUser()).thenReturn(receiver);
        when(receiver.getUserId()).thenReturn(3L);
        when(notification.getNotificationId()).thenReturn(7L);
        when(notification.getNotificationType()).thenReturn(NotificationType.COMMENT);
        when(reader.loadEnabledSubscriptions(3L)).thenReturn(List.of(
                new PushSubscriptionSnapshot(11L, 3L, "https://push/1", "k1", "a1",
                        LocalDateTime.of(2026, 7, 17, 23, 59)),
                new PushSubscriptionSnapshot(12L, 3L, "https://push/2", "k2", "a2",
                        LocalDateTime.of(2026, 7, 17, 23, 59))));
        when(payloadFactory.create(org.mockito.ArgumentMatchers.any())).thenReturn("payload");
        when(repository.insertIfAbsent(org.mockito.ArgumentMatchers.any())).thenReturn(true, false);

        assertThat(enqueuer.enqueue(event, notification)).isEqualTo(1);
    }
}
