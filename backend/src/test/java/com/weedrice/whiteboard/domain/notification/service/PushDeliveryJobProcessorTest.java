package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushDeliveryJobProcessorTest {

    private static final Long JOB_ID = 5L;

    @Mock PushDeliveryJobRepository jobRepository;
    @Mock PushDeliveryJobTransaction jobTransaction;
    @Mock PushDispatchSnapshotReader snapshotReader;
    @Mock PushNotificationDispatcher dispatcher;
    @Mock PushDeliveryJobMetrics metrics;

    private Clock clock;
    private LocalDateTime now;
    private PushSubscriptionSnapshot subscription;
    private PushDeliveryLease lease;
    private PushDeliveryJobProcessor processor;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00.123456789Z"), ZoneOffset.UTC);
        now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        subscription = new PushSubscriptionSnapshot(
                9L, 2L, "https://push.example/endpoint", "key", "auth", now.minusMinutes(1));
        lease = new PushDeliveryLease(JOB_ID, now, subscription, "payload");
        processor = new PushDeliveryJobProcessor(
                jobRepository, jobTransaction, snapshotReader, dispatcher, metrics, clock);
        when(jobTransaction.claim(JOB_ID, now)).thenReturn(lease);
    }

    @Test
    void successCompletesClaimedJob() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.SUCCESS);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(jobTransaction).complete(lease);
        verify(metrics).recordOutcome("success");
    }

    @Test
    void retryableFailureSchedulesBackoff() {
        PushDeliveryJob job = org.mockito.Mockito.mock(PushDeliveryJob.class);
        when(job.getRetryCount()).thenReturn(0);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.RETRYABLE_FAILURE);
        when(jobTransaction.retry(lease, "Retryable push response", now, now.plusMinutes(1)))
                .thenReturn(false);

        assertThat(processor.processJob(JOB_ID)).isFalse();

        verify(jobTransaction).retry(lease, "Retryable push response", now, now.plusMinutes(1));
        verify(metrics).recordOutcome("retry");
    }

    @Test
    void changedSubscriptionExpiresWithoutSending() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(false);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(jobTransaction).expire(lease, "Subscription changed or push disabled", false);
        verify(metrics).recordOutcome("stale_subscription");
        org.mockito.Mockito.verifyNoInteractions(dispatcher);
    }
}
