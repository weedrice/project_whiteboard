package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.anyList;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

class SemanticSearchJobRepositoryTest {

    private final JdbcTemplate jdbcTemplate = mock(JdbcTemplate.class);
    private final SemanticSearchJobRepository jobRepository = new SemanticSearchJobRepository(jdbcTemplate);

    @Test
    void enqueueAll_batchesContentIds() {
        jobRepository.enqueueAll("POST", List.of(1L, 2L), SemanticSearchIndexAction.UPSERT);

        @SuppressWarnings("unchecked")
        ArgumentCaptor<List<Object[]>> batchCaptor = ArgumentCaptor.forClass(List.class);
        verify(jdbcTemplate).batchUpdate(contains("INSERT INTO semantic_search_jobs"), batchCaptor.capture());
        assertThat(batchCaptor.getValue())
                .extracting(args -> args[0], args -> args[1], args -> args[2])
                .containsExactly(
                        org.assertj.core.groups.Tuple.tuple("POST", 1L, "UPSERT"),
                        org.assertj.core.groups.Tuple.tuple("POST", 2L, "UPSERT"));
    }

    @Test
    void enqueueAll_skipsEmptyContentIds() {
        jobRepository.enqueueAll("POST", List.of(), SemanticSearchIndexAction.UPSERT);

        verify(jdbcTemplate, never()).batchUpdate(contains("INSERT INTO semantic_search_jobs"), anyList());
    }
}
