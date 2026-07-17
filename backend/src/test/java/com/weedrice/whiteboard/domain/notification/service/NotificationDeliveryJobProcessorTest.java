package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
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

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
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
                .thenReturn(false);
        NotificationDeliveryJobProcessor processor = new NotificationDeliveryJobProcessor(
                jobRepository,
                jobTransaction,
                metrics,
                clock);

        assertThat(processor.processJob(5L)).isFalse();

        verify(jobTransaction).fail(
                5L,
                claimedAt,
                "IllegalStateException",
                claimedAt.plusMinutes(1));
        verify(metrics).recordOutcome("retry");
    }
}
