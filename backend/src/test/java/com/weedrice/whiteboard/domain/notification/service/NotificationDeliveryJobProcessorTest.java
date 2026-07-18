package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.config.NotificationDeliveryJobProperties;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationDeliveryJobProcessorTest {

    @Mock
    private NotificationDeliveryJobRepository jobRepository;
    @Mock
    private NotificationDeliveryJobTransaction jobTransaction;
    @Mock
    private NotificationDeliveryJobMetrics metrics;

    @Test
    void processJob_retriesWithBackoffWhenDeliveryFails() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime claimedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        NotificationDeliveryJob job = org.mockito.Mockito.mock(NotificationDeliveryJob.class);
        when(job.getRetryCount()).thenReturn(0);
        when(jobRepository.findById(5L)).thenReturn(Optional.of(job));
        when(jobTransaction.claim(5L, claimedAt)).thenReturn(claimedAt);
        org.mockito.Mockito.doThrow(new IllegalStateException("database unavailable"))
                .when(jobTransaction).deliver(5L, claimedAt);
        when(jobTransaction.fail(eq(5L), eq(claimedAt), eq("IllegalStateException"), any()))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_RETRY);
        NotificationDeliveryJobProcessor processor = new NotificationDeliveryJobProcessor(
                jobRepository,
                jobTransaction,
                metrics,
                properties(),
                clock);

        assertThat(processor.processJob(5L)).isFalse();

        verify(jobTransaction).fail(
                5L,
                claimedAt,
                "IllegalStateException",
                claimedAt.plusMinutes(1));
        verify(metrics).recordOutcome("retry");
    }

    @Test
    void processJob_doesNotReportInvalidPayloadAsSuccess() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime claimedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        when(jobTransaction.claim(5L, claimedAt)).thenReturn(claimedAt);
        when(jobTransaction.deliver(5L, claimedAt))
                .thenReturn(DeliveryJobTransitionResult.REJECTED_INVALID);
        NotificationDeliveryJobProcessor processor = new NotificationDeliveryJobProcessor(
                jobRepository,
                jobTransaction,
                metrics,
                properties(),
                clock);

        assertThat(processor.processJob(5L)).isFalse();

        verify(metrics).recordOutcome("invalid_payload");
        verify(metrics, never()).recordOutcome("success");
    }

    @Test
    void processJob_reportsLeaseLossWithoutSuccess() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        LocalDateTime claimedAt = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC);
        when(jobTransaction.claim(5L, claimedAt)).thenReturn(claimedAt);
        when(jobTransaction.deliver(5L, claimedAt))
                .thenReturn(DeliveryJobTransitionResult.LEASE_LOST);
        NotificationDeliveryJobProcessor processor = new NotificationDeliveryJobProcessor(
                jobRepository,
                jobTransaction,
                metrics,
                properties(),
                clock);

        assertThat(processor.processJob(5L)).isFalse();

        verify(metrics).recordOutcome("lease_lost");
        verify(metrics, never()).recordOutcome("success");
    }

    @Test
    void processDueJobs_limitsCleanupToConfiguredBatchCount() {
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T00:00:00Z"), ZoneOffset.UTC);
        NotificationDeliveryJobProperties properties = properties();
        properties.setCleanupBatchSize(2);
        properties.setCleanupMaxBatches(2);
        when(jobRepository.findStaleProcessingJobIds(any(), any())).thenReturn(List.of());
        when(jobRepository.findDueJobIds(any(), any())).thenReturn(List.of());
        when(jobTransaction.deleteCompletedBefore(any(), eq(2))).thenReturn(2);
        when(jobTransaction.deleteFailedBefore(any(), eq(2))).thenReturn(1);
        when(jobRepository.findOldestPendingAttemptAt()).thenReturn(Optional.empty());
        when(jobRepository.findOldestProcessingStartedAt()).thenReturn(Optional.empty());
        NotificationDeliveryJobProcessor processor = new NotificationDeliveryJobProcessor(
                jobRepository, jobTransaction, metrics, properties, clock);

        assertThat(processor.processDueJobs()).isZero();

        verify(jobTransaction, times(2)).deleteCompletedBefore(any(), eq(2));
        verify(jobTransaction).deleteFailedBefore(any(), eq(2));
    }

    private NotificationDeliveryJobProperties properties() {
        return new NotificationDeliveryJobProperties();
    }
}
