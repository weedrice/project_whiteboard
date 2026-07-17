package com.weedrice.whiteboard.domain.mqueue.service;

import com.weedrice.whiteboard.domain.mqueue.MqueueRetentionProperties;
import com.weedrice.whiteboard.domain.mqueue.repository.MessageQueueRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class MqueueCleanupService {

    private final MessageQueueRepository messageQueueRepository;
    private final MqueueRetentionProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;

    @Transactional
    public int deleteExpiredTerminalBatch() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(properties.getTerminalRetentionDays());
        int deleted = messageQueueRepository.deleteTerminalBatch(cutoff, properties.getCleanupBatchSize());
        if (deleted > 0) {
            cleanupCounter().increment(deleted);
        }
        return deleted;
    }

    private Counter cleanupCounter() {
        return meterRegistry.counter("noviis.message.queue.terminal.cleaned");
    }
}
