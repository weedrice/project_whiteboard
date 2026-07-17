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
                .thenReturn(List.of(1L, 2L))
                .thenReturn(List.of(11L));

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
                        .contains("j.status IN ('PENDING', 'PROCESSING')")
                        .contains("b.is_active = 'Y'")
                        .contains("b.is_public = 'Y'")
                        .contains("status = 'ACTIVE'")
                        .contains("deleted_at IS NULL"));
        assertThat(sqlCaptor.getAllValues().get(0)).contains("p.is_secret = 'N'");
        assertThat(sqlCaptor.getAllValues().get(1)).contains("p.is_secret = 'N'");
    }

    @Test
    void enqueueBackfill_excludesBlindedRowsBeforeApplyingLimit() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(100)))
                .thenReturn(List.of(101L))
                .thenReturn(List.of(201L));

        backfillService.enqueueBackfill("ALL", 100);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(sqlCaptor.capture(), eq(Long.class), eq(100));
        String postSql = sqlCaptor.getAllValues().get(0);
        String commentSql = sqlCaptor.getAllValues().get(1);

        assertThat(postSql)
                .contains("p.is_blinded = 'N'")
                .satisfies(sql -> assertThat(sql.indexOf("p.is_blinded = 'N'"))
                        .isLessThan(sql.indexOf("LIMIT ?")));
        assertThat(commentSql)
                .contains("c.is_blinded = 'N'")
                .contains("p.is_blinded = 'N'")
                .satisfies(sql -> assertThat(sql.indexOf("c.is_blinded = 'N'"))
                        .isLessThan(sql.indexOf("LIMIT ?")))
                .satisfies(sql -> assertThat(sql.indexOf("p.is_blinded = 'N'"))
                        .isLessThan(sql.indexOf("LIMIT ?")));
        verify(jobRepository).enqueueAll("POST", List.of(101L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(201L), SemanticSearchIndexAction.UPSERT);
    }

    @Test
    void enqueueBackfill_deprioritizesFailedJobsBeforeLimitWithoutExcludingRetry() {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(50)))
                .thenReturn(List.of(301L))
                .thenReturn(List.of(401L));

        backfillService.enqueueBackfill("ALL", 50);

        ArgumentCaptor<String> sqlCaptor = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(sqlCaptor.capture(), eq(Long.class), eq(50));
        assertThat(sqlCaptor.getAllValues()).allSatisfy(sql -> {
            assertThat(sql)
                    .contains("j.status IN ('PENDING', 'PROCESSING')")
                    .contains("failed_job.status = 'FAILED'")
                    .contains("THEN 1 ELSE 0 END ASC");
            assertThat(sql.indexOf("failed_job.status = 'FAILED'"))
                    .isLessThan(sql.indexOf("created_at ASC"));
            assertThat(sql.indexOf("created_at ASC"))
                    .isLessThan(sql.indexOf("LIMIT ?"));
        });
        verify(jobRepository).enqueueAll("POST", List.of(301L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(401L), SemanticSearchIndexAction.UPSERT);
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
                .thenReturn(List.of(1L))
                .thenReturn(List.of(11L));
        doThrow(new IllegalStateException("comment enqueue failed"))
                .when(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);

        assertThatThrownBy(() -> backfillService.enqueueBackfill("ALL", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment enqueue failed");

        verify(jobRepository).enqueueAll("POST", List.of(1L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);
    }
}
