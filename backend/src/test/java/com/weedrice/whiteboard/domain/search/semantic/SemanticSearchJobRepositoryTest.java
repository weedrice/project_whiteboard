package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class SemanticSearchJobRepositoryTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SemanticSearchJobRepository jobRepository = new SemanticSearchJobRepository(jdbcTemplate, FIXED_CLOCK);

    @Test
    void enqueueAll_batchesContentIds() {
        jobRepository.enqueueAll("POST", List.of(1L, 2L), SemanticSearchIndexAction.UPSERT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(contains("INSERT INTO semantic_search_jobs"), batchCaptor.capture());
        assertThat(batchCaptor.getValue())
                .extracting(args -> args[0], args -> args[1], args -> args[2], args -> args[3], args -> args[4])
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST", 1L, "UPSERT", FIXED_NOW, FIXED_NOW),
                        org.assertj.core.groups.Tuple.tuple("POST", 2L, "UPSERT", FIXED_NOW, FIXED_NOW));
    }

    @Test
    void reservationsUseAscendingKeyOrderAndOnlyReturnNewRows() {
        jobRepository.reserveJobKeys("POST", List.of(9L, 1L));
        ArgumentCaptor<String> sql = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate).queryForList(sql.capture(), eq(Long.class), eq("POST"), eq(FIXED_NOW), eq(FIXED_NOW),
                eq(FIXED_NOW), eq(FIXED_NOW), any(SqlArrayValue.class));
        assertThat(sql.getValue().replaceAll("\\s+", " "))
                .contains("ORDER BY candidate_id ON CONFLICT (content_type, content_id) "
                        + "DO UPDATE SET action = EXCLUDED.action WHERE FALSE RETURNING content_id");
    }

    @Test
    void emptyDeleteProbeRollsBackBeforeReleasingSavepoint() {
        when(jdbcTemplate.queryForList(contains("FOR UPDATE SKIP LOCKED"), eq(Long.class), eq("POST"), eq(1)))
                .thenReturn(List.of());

        assertThat(jobRepository.probeFailedDeletes("POST", 1)).isZero();

        var order = inOrder(jdbcTemplate);
        order.verify(jdbcTemplate).execute("SAVEPOINT backfill_delete_batch");
        order.verify(jdbcTemplate).queryForList(contains("FOR UPDATE SKIP LOCKED"), eq(Long.class), eq("POST"), eq(1));
        order.verify(jdbcTemplate).execute("ROLLBACK TO SAVEPOINT backfill_delete_batch");
        order.verify(jdbcTemplate).execute("RELEASE SAVEPOINT backfill_delete_batch");
    }

    @Test
    void enqueueAll_skipsEmptyContentIds() {
        jobRepository.enqueueAll("POST", List.of(), SemanticSearchIndexAction.UPSERT);

        verify(jdbcTemplate, never()).batchUpdate(contains("INSERT INTO semantic_search_jobs"), anyList());
    }
}
