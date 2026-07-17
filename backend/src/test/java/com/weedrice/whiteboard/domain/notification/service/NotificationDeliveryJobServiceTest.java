package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryJobServiceTest {

    @Mock
    private NotificationDeliveryJobRepository jobRepository;
    @Mock
    private NotificationDeliveryJobKickoff kickoff;

    @Test
    void enqueue_persistsScalarSnapshotAndSchedulesProcessing() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        NotificationDeliveryJobService service = new NotificationDeliveryJobService(
                jobRepository, kickoff, clock, new NotificationPayloadValidator());
        User receiver = user(1L);
        User actor = user(2L);
        NotificationEvent event = NotificationEvent.localized(
                receiver,
                actor,
                NotificationType.COMMENT,
                NotificationSourceType.POST,
                10L,
                "notification.comment.created",
                "writer");
        when(jobRepository.save(any(NotificationDeliveryJob.class))).thenAnswer(invocation -> {
            NotificationDeliveryJob job = invocation.getArgument(0);
            ReflectionTestUtils.setField(job, "jobId", 7L);
            return job;
        });

        NotificationDeliveryJob saved = service.enqueue(event);

        assertThat(saved.getReceiverUserId()).isEqualTo(1L);
        assertThat(saved.getActorUserId()).isEqualTo(2L);
        assertThat(saved.getStatus()).isEqualTo(NotificationDeliveryJob.Status.PENDING);
        assertThat(saved.getMessageKey()).isEqualTo("notification.comment.created");
        verify(kickoff).process(7L);
    }

    @Test
    void enqueue_ignoresIncompleteEvent() {
        NotificationDeliveryJobService service = new NotificationDeliveryJobService(
                jobRepository,
                kickoff,
                Clock.systemUTC(),
                new NotificationPayloadValidator());
        NotificationEvent event = new NotificationEvent(
                User.builder().build(),
                null,
                NotificationType.SYSTEM,
                NotificationSourceType.SYSTEM,
                null,
                "content");

        assertThat(service.enqueue(event)).isNull();
        verify(jobRepository, never()).save(any());
        verify(kickoff, never()).process(any());
    }

    @Test
    void enqueue_sameEventObjectReusesItsDurableJob() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        NotificationDeliveryJobService service = new NotificationDeliveryJobService(
                jobRepository, kickoff, clock, new NotificationPayloadValidator());
        NotificationEvent event = new NotificationEvent(
                user(1L),
                user(2L),
                NotificationType.LIKE,
                NotificationSourceType.POST,
                10L,
                "like");
        NotificationDeliveryJob saved = NotificationDeliveryJob.from(
                event,
                java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        ReflectionTestUtils.setField(saved, "jobId", 9L);
        when(jobRepository.findByEventId(event.getEventId()))
                .thenReturn(java.util.Optional.empty())
                .thenReturn(java.util.Optional.of(saved));
        when(jobRepository.save(any(NotificationDeliveryJob.class))).thenReturn(saved);

        assertThat(service.enqueue(event)).isSameAs(saved);
        assertThat(service.enqueue(event)).isSameAs(saved);

        verify(jobRepository, times(1)).save(any(NotificationDeliveryJob.class));
        verify(kickoff, times(1)).process(9L);
    }

    @Test
    void enqueue_invalidDeliverablePayload_isPersistedAsDeadLetterWithoutKickoff() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        NotificationDeliveryJobService service = new NotificationDeliveryJobService(
                jobRepository, kickoff, clock, new NotificationPayloadValidator());
        NotificationEvent event = new NotificationEvent(
                user(1L), null, NotificationType.SYSTEM, NotificationSourceType.SYSTEM, null, "content");
        when(jobRepository.save(any(NotificationDeliveryJob.class))).thenAnswer(invocation -> invocation.getArgument(0));

        NotificationDeliveryJob job = service.enqueue(event);

        assertThat(job.getStatus()).isEqualTo(NotificationDeliveryJob.Status.FAILED);
        assertThat(job.getFailureCode()).isEqualTo("INVALID_PAYLOAD");
        verify(kickoff, never()).process(any());
    }

    @Test
    void failedJob_redriveResetsRetryStateAndCountsAttempts() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        NotificationEvent event = new NotificationEvent(
                user(1L), null, NotificationType.SYSTEM, NotificationSourceType.SYSTEM, 1L, "content");
        NotificationDeliveryJob job = NotificationDeliveryJob.from(
                event, java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));
        job.rejectInvalidPayload("bad", java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC));

        assertThat(job.redrive(java.time.LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC))).isTrue();
        assertThat(job.getStatus()).isEqualTo(NotificationDeliveryJob.Status.PENDING);
        assertThat(job.getRedriveCount()).isEqualTo(1);
        assertThat(job.getFailureCode()).isNull();
    }

    private User user(Long id) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }
}
