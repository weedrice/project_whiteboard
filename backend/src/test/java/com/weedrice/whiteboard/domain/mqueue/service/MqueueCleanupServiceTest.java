package com.weedrice.whiteboard.domain.mqueue.service;

import com.weedrice.whiteboard.domain.mqueue.MqueueRetentionProperties;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class MqueueCleanupServiceTest {

    @Mock
    private MessageQueueRepository messageQueueRepository;

    @Test
    void deletesConfiguredBatchBeforeRetentionCutoffAndRecordsCount() {
        MqueueRetentionProperties properties = new MqueueRetentionProperties();
        properties.setTerminalRetentionDays(30);
        properties.setCleanupBatchSize(200);
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T03:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MqueueCleanupService service = new MqueueCleanupService(
                messageQueueRepository, properties, clock, meterRegistry);
        LocalDateTime cutoff = LocalDateTime.of(2026, 6, 17, 3, 0);
        when(messageQueueRepository.deleteTerminalBatch(cutoff, 200)).thenReturn(17);

        int deleted = service.deleteExpiredTerminalBatch();

        assertThat(deleted).isEqualTo(17);
        verify(messageQueueRepository).deleteTerminalBatch(cutoff, 200);
        assertThat(meterRegistry.counter("noviis.message.queue.terminal.cleaned").count())
                .isEqualTo(17.0);
    }

    @Test
    void deletesDeliveredUnconfirmedAfterInvestigationGraceAndRecordsCount() {
        MqueueRetentionProperties properties = new MqueueRetentionProperties();
        properties.setDeliveredUnconfirmedRetentionDays(7);
        properties.setCleanupBatchSize(100);
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T03:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        MqueueCleanupService service = new MqueueCleanupService(
                messageQueueRepository, properties, clock, meterRegistry);
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 10, 3, 0);
        when(messageQueueRepository.deleteDeliveredUnconfirmedBatch(cutoff, 100)).thenReturn(5);

        int deleted = service.deleteExpiredDeliveredUnconfirmedBatch();

        assertThat(deleted).isEqualTo(5);
        verify(messageQueueRepository).deleteDeliveredUnconfirmedBatch(cutoff, 100);
        assertThat(meterRegistry.counter("noviis.message.queue.delivered.unconfirmed.cleaned").count())
                .isEqualTo(5.0);
    }
}
