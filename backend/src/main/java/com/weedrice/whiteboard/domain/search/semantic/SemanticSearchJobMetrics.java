package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Slf4j
@Component
class SemanticSearchJobMetrics {

    private final SemanticSearchJobRepository jobRepository;
    private final Clock clock;
    private final AtomicLong pendingCount = new AtomicLong();
    private final AtomicLong oldestPendingAgeSeconds = new AtomicLong();

    SemanticSearchJobMetrics(
            SemanticSearchJobRepository jobRepository,
            Clock clock,
            MeterRegistry meterRegistry) {
        this.jobRepository = jobRepository;
        this.clock = clock;
        Gauge.builder("noviis.semantic.jobs.pending", pendingCount, AtomicLong::get)
                .register(meterRegistry);
        Gauge.builder("noviis.semantic.jobs.oldest.age", oldestPendingAgeSeconds, AtomicLong::get)
                .baseUnit("seconds")
                .register(meterRegistry);
    }

    @Scheduled(fixedDelay = 30_000, scheduler = "taskScheduler")
    public void refresh() {
        try {
            pendingCount.set(jobRepository.countPendingJobs());
            long age = jobRepository.findOldestPendingCreatedAt()
                    .map(createdAt -> Duration.between(createdAt, LocalDateTime.now(clock)).getSeconds())
                    .orElse(0L);
            oldestPendingAgeSeconds.set(Math.max(0L, age));
        } catch (RuntimeException exception) {
            log.warn("Failed to refresh semantic search backlog metrics", exception);
        }
    }
}
