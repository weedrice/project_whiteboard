package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

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
    void enqueueAll_skipsEmptyContentIds() {
        jobRepository.enqueueAll("POST", List.of(), SemanticSearchIndexAction.UPSERT);

        verify(jdbcTemplate, never()).batchUpdate(contains("INSERT INTO semantic_search_jobs"), anyList());
    }
}
