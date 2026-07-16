package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class SemanticSearchJobMetricsTest {

    private final SemanticSearchJobRepository repository = mock(SemanticSearchJobRepository.class);
    private final SimpleMeterRegistry registry = new SimpleMeterRegistry();
    private final Clock clock = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
    private final SemanticSearchJobMetrics metrics = new SemanticSearchJobMetrics(repository, clock, registry);

    @Test
    void refreshesBothGaugesTogether() {
        when(repository.countPendingJobs()).thenReturn(7L);
        when(repository.findOldestPendingCreatedAt())
                .thenReturn(Optional.of(LocalDateTime.of(2026, 7, 17, 11, 58)));

        metrics.refresh();

        assertThat(gauge("noviis.semantic.jobs.pending")).isEqualTo(7);
        assertThat(gauge("noviis.semantic.jobs.oldest.age")).isEqualTo(120);
    }

    @Test
    void leavesBothGaugesUnchangedWhenSecondQueryFails() {
        when(repository.countPendingJobs()).thenReturn(3L);
        when(repository.findOldestPendingCreatedAt())
                .thenReturn(Optional.of(LocalDateTime.of(2026, 7, 17, 11, 59)));
        metrics.refresh();

        when(repository.countPendingJobs()).thenReturn(9L);
        when(repository.findOldestPendingCreatedAt()).thenThrow(new IllegalStateException("database unavailable"));

        assertThatThrownBy(metrics::refresh)
                .isInstanceOf(IllegalStateException.class)
                .hasMessage("database unavailable");
        assertThat(gauge("noviis.semantic.jobs.pending")).isEqualTo(3);
        assertThat(gauge("noviis.semantic.jobs.oldest.age")).isEqualTo(60);
    }

    private double gauge(String name) {
        return registry.get(name).gauge().value();
    }
}
