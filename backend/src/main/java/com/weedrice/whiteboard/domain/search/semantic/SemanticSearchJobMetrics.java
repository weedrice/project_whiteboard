package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.Gauge;
import io.micrometer.core.instrument.MeterRegistry;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

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
        long nextPendingCount = jobRepository.countPendingJobs();
        long nextOldestPendingAgeSeconds = jobRepository.findOldestPendingCreatedAt()
                .map(createdAt -> Duration.between(createdAt, LocalDateTime.now(clock)).getSeconds())
                .orElse(0L);
        pendingCount.set(nextPendingCount);
        oldestPendingAgeSeconds.set(Math.max(0L, nextOldestPendingAgeSeconds));
    }
}
