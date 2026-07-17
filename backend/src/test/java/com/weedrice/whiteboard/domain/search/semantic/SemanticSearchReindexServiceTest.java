package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.transaction.TransactionStatus;
import org.springframework.transaction.support.TransactionCallback;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

class SemanticSearchReindexServiceTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 17, 12, 0);

    private SemanticSearchReindexRepository reindexRepository;
    private SemanticSearchJobRepository jobRepository;
    private TransactionTemplate transactionTemplate;
    private SimpleMeterRegistry meterRegistry;
    private SemanticSearchReindexService service;

    @BeforeEach
    void setUp() {
        reindexRepository = mock(SemanticSearchReindexRepository.class);
        jobRepository = mock(SemanticSearchJobRepository.class);
        transactionTemplate = mock(TransactionTemplate.class);
        meterRegistry = new SimpleMeterRegistry();
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T12:00:00Z"), ZoneOffset.UTC);
        service = new SemanticSearchReindexService(
                reindexRepository,
                jobRepository,
                transactionTemplate,
                clock,
                new SemanticSearchReindexMetrics(meterRegistry));

        when(transactionTemplate.execute(any())).thenAnswer(invocation -> {
            @SuppressWarnings("unchecked")
            TransactionCallback<Object> callback = invocation.getArgument(0);
            return callback.doInTransaction(mock(TransactionStatus.class));
        });
        when(reindexRepository.findOldestActiveAt()).thenReturn(null);
    }

    @Test
    void poisonJobIsBackedOffWithoutStarvingLaterJob() {
        when(reindexRepository.findRunnableIds(NOW, 20)).thenReturn(List.of(1L, 2L));
        when(reindexRepository.claim(eq(1L), any(UUID.class), eq(NOW))).thenReturn(1);
        when(reindexRepository.claim(eq(2L), any(UUID.class), eq(NOW))).thenReturn(1);
        when(reindexRepository.findClaimed(eq(1L), any(UUID.class))).thenAnswer(invocation ->
                cursor(1L, 10L, 0, invocation.getArgument(1)));
        when(reindexRepository.findClaimed(eq(2L), any(UUID.class))).thenAnswer(invocation ->
                cursor(2L, 20L, 0, invocation.getArgument(1)));
        when(jobRepository.findActiveCommentIdsByPostIdAfter(10L, 0L, 500))
                .thenThrow(new IllegalStateException("poison page"));
        when(jobRepository.findActiveCommentIdsByPostIdAfter(20L, 0L, 500))
                .thenReturn(List.of());
        when(reindexRepository.markFailed(
                eq(1L), any(UUID.class), eq(5), eq(NOW.plusMinutes(1)), eq(NOW),
                eq("IllegalStateException: poison page")))
                .thenReturn(1);
        when(reindexRepository.advance(
                eq(2L), any(UUID.class), eq("COMMENT"), eq(0L), eq(true), eq(NOW)))
                .thenReturn(1);

        int processed = service.processPages();

        assertThat(processed).isEqualTo(1);
        verify(reindexRepository).markFailed(
                eq(1L), any(UUID.class), eq(5), eq(NOW.plusMinutes(1)), eq(NOW),
                eq("IllegalStateException: poison page"));
        verify(reindexRepository).advance(
                eq(2L), any(UUID.class), eq("COMMENT"), eq(0L), eq(true), eq(NOW));
        assertThat(counter("retry")).isEqualTo(1);
        assertThat(counter("success")).isEqualTo(1);
    }

    @Test
    void fifthFailureMovesJobToDeadLetterMetric() {
        when(reindexRepository.findRunnableIds(NOW, 20)).thenReturn(List.of(1L));
        when(reindexRepository.claim(eq(1L), any(UUID.class), eq(NOW))).thenReturn(1);
        when(reindexRepository.findClaimed(eq(1L), any(UUID.class))).thenAnswer(invocation ->
                cursor(1L, 10L, 4, invocation.getArgument(1)));
        when(jobRepository.findActiveCommentIdsByPostIdAfter(10L, 0L, 500))
                .thenThrow(new IllegalStateException("still broken"));
        when(reindexRepository.markFailed(
                eq(1L), any(UUID.class), eq(5), eq(NOW.plusMinutes(16)), eq(NOW), anyString()))
                .thenReturn(1);

        assertThat(service.processPages()).isZero();

        assertThat(counter("dead_letter")).isEqualTo(1);
        assertThat(counter("retry")).isZero();
    }

    @Test
    void redriveOnlyAcceptsFailedJob() {
        when(reindexRepository.redrive(11L, NOW)).thenReturn(1);

        service.redrive(11L);

        assertThat(counter("redrive")).isEqualTo(1);

        when(reindexRepository.redrive(12L, NOW)).thenReturn(0);
        assertThatThrownBy(() -> service.redrive(12L))
                .isInstanceOf(BusinessException.class);
    }

    private SemanticSearchReindexRepository.ReindexCursor cursor(
            long id, long scopeId, int retryCount, UUID leaseToken) {
        return new SemanticSearchReindexRepository.ReindexCursor(
                id, "POST_COMMENTS", scopeId, "COMMENT", 0L, retryCount, leaseToken);
    }

    private double counter(String result) {
        return meterRegistry.get("noviis.semantic.reindex.jobs.processed")
                .tag("result", result)
                .counter()
                .count();
    }
}

