package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SemanticSearchBackfillServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SemanticSearchJobService jobService;
    private SemanticSearchBackfillService backfillService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        jobService = mock(SemanticSearchJobService.class);
        backfillService = new SemanticSearchBackfillService(jdbcTemplate, jobService);
    }

    @Test
    void enqueueBackfill_selectsOnlyMissingOrTombstonedEmbeddings() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(10)))
                .thenReturn(List.of(1L, 2L), List.of(11L));

        int count = backfillService.enqueueBackfill("ALL", 10);

        assertThat(count).isEqualTo(3);
        verify(jobService).enqueueAll("POST", List.of(1L, 2L), SemanticSearchIndexAction.UPSERT);
        verify(jobService).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(sqlCaptor.capture(), eq(Long.class), eq(10));
        assertThat(sqlCaptor.getAllValues())
                .allSatisfy(sql -> assertThat(sql)
                        .contains("LEFT JOIN semantic_search_embeddings")
                        .contains("e.embedding_id IS NULL OR e.deleted_at IS NOT NULL")
                        .contains("semantic_search_jobs j")
                        .contains("j.status IN ('PENDING', 'PROCESSING')"));
    }

    @Test
    void enqueueBackfill_rejectsInvalidLimit() {
        assertThatThrownBy(() -> backfillService.enqueueBackfill("POST", 0))
                .isInstanceOf(BusinessException.class);

        verifyNoInteractions(jdbcTemplate, jobService);
    }
}
