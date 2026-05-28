package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

class SemanticSearchBackfillServiceTest {

    private JdbcTemplate jdbcTemplate;
    private SemanticSearchJobRepository jobRepository;
    private SemanticSearchBackfillService backfillService;

    @BeforeEach
    void setUp() {
        jdbcTemplate = mock(JdbcTemplate.class);
        jobRepository = mock(SemanticSearchJobRepository.class);
        backfillService = new SemanticSearchBackfillService(jdbcTemplate, jobRepository);
    }

    @Test
    void enqueueBackfill_selectsOnlyMissingOrTombstonedEmbeddings() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(10)))
                .thenReturn(List.of(1L, 2L), List.of(11L));

        int count = backfillService.enqueueBackfill("ALL", 10);

        assertThat(count).isEqualTo(3);
        verify(jobRepository).enqueueAll("POST", List.of(1L, 2L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);

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

        verifyNoInteractions(jdbcTemplate, jobRepository);
    }

    @Test
    void enqueueBackfill_usesRequiredWriteTransaction() throws NoSuchMethodException {
        Method method = SemanticSearchBackfillService.class.getMethod(
                "enqueueBackfill", String.class, int.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
        assertThat(transactional.readOnly()).isFalse();
    }

    @Test
    void enqueueBackfill_propagatesCommentEnqueueFailureAfterPostEnqueue() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(10)))
                .thenReturn(List.of(1L), List.of(11L));
        doThrow(new IllegalStateException("comment enqueue failed"))
                .when(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);

        assertThatThrownBy(() -> backfillService.enqueueBackfill("ALL", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment enqueue failed");

        verify(jobRepository).enqueueAll("POST", List.of(1L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);
    }
}
