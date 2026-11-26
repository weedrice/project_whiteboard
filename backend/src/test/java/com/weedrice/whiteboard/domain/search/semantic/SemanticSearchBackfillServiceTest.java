package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.ArgumentCaptor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.SqlArrayValue;
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

    private void repairs(String type, int limit, List<Long> candidates, int remaining, List<Long> eligible) {
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(type), eq(type),
                any(SqlArrayValue.class), eq(limit))).thenReturn(candidates);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq(type), eq(type),
                any(SqlArrayValue.class), any(SqlArrayValue.class), eq(remaining))).thenReturn(eligible);
    }

    @Test
    void bothTypesSelectAndRecheckVisibleMissingTombstonedOrFailedUpserts() {
        repairs("POST", 10, List.of(1L, 2L), 10, List.of(1L, 2L));
        repairs("COMMENT", 10, List.of(11L), 10, List.of(11L));

        assertThat(backfillService.enqueueBackfill("ALL", 10)).isEqualTo(3);

        verify(jobRepository).enqueueAll("POST", List.of(1L, 2L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);
        ArgumentCaptor<String> selection = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> recheck = ArgumentCaptor.forClass(String.class);
        verify(jdbcTemplate, times(2)).queryForList(selection.capture(), eq(Long.class), anyString(), anyString(),
                any(SqlArrayValue.class), eq(10));
        verify(jdbcTemplate, times(2)).queryForList(recheck.capture(), eq(Long.class), anyString(), anyString(),
                any(SqlArrayValue.class), any(SqlArrayValue.class), eq(10));
        for (List<String> queries : List.of(selection.getAllValues(), recheck.getAllValues())) {
            assertThat(queries).allSatisfy(sql -> assertThat(sql)
                    .contains("p.is_secret = 'N'", "p.is_blinded = 'N'", "b.is_active = 'Y'", "b.is_public = 'Y'")
                    .contains("status = 'ACTIVE'", "status = 'DELETED'", "deleted_at IS NULL")
                    .contains("j.job_id IS NULL OR j.status = 'COMPLETED' OR (j.status = 'FAILED' AND j.action = 'UPSERT')")
                    .contains("e.embedding_id IS NULL OR e.deleted_at IS NOT NULL OR (j.status = 'FAILED' AND j.action = 'UPSERT')"));
            assertThat(queries.get(1)).contains("c.is_blinded = 'N'");
            assertThat(queries).allSatisfy(sql -> assertThat(sql.replaceAll("\\s+", " "))
                    .contains("ORDER BY CASE WHEN j.status = 'FAILED' THEN 1 ELSE 0 END ASC, "
                            + "source.created_at ASC, source.content_id ASC LIMIT ?"));
        }
        assertThat(recheck.getAllValues()).allSatisfy(sql -> assertThat(sql)
                .contains("AND source.content_id = ANY(?)"));
    }

    @Test
    void countsOnlySuccessfulRedrivesAndEligibleRepairsAndReleasesOnlyItsUnusedReservations() {
        List<Long> candidates = List.of(1L, 3L, 4L);
        when(jobRepository.probeFailedDeletes("POST", 6)).thenReturn(2);
        repairs("POST", 4, candidates, 5, List.of(1L, 3L));
        when(jobRepository.reserveJobKeys("POST", candidates)).thenReturn(List.of(1L, 4L));
        when(jobRepository.redriveUnlockedDeletes("POST", 6)).thenReturn(1);

        assertThat(backfillService.enqueueBackfill("POST", 6)).isEqualTo(3);

        var order = inOrder(jobRepository, jdbcTemplate);
        order.verify(jobRepository).probeFailedDeletes("POST", 6);
        order.verify(jdbcTemplate).queryForList(anyString(), eq(Long.class), eq("POST"), eq("POST"),
                any(SqlArrayValue.class), eq(4));
        order.verify(jobRepository).reserveJobKeys("POST", candidates);
        order.verify(jobRepository).redriveUnlockedDeletes("POST", 6);
        order.verify(jdbcTemplate).queryForList(anyString(), eq(Long.class), eq("POST"), eq("POST"),
                any(SqlArrayValue.class), any(SqlArrayValue.class), eq(5));
        order.verify(jobRepository).enqueueAll("POST", List.of(1L, 3L), SemanticSearchIndexAction.UPSERT);
        order.verify(jobRepository).discardUnusedReservations("POST", List.of(4L));
    }

    @Test
    void partialDeleteBatchUsesRemainingCapacityForTheHighestPriorityRepairs() {
        List<Long> candidates = List.of(30L, 20L);
        when(jobRepository.probeFailedDeletes("POST", 3)).thenReturn(1);
        repairs("POST", 2, candidates, 1, List.of(30L));
        when(jobRepository.reserveJobKeys("POST", candidates)).thenReturn(candidates);
        when(jobRepository.redriveUnlockedDeletes("POST", 3)).thenReturn(2);

        assertThat(backfillService.enqueueBackfill("POST", 3)).isEqualTo(3);

        verify(jobRepository).enqueueAll("POST", List.of(30L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).discardUnusedReservations("POST", List.of(20L));
    }

    @Test
    void lostDeleteSlotRetriesTheMixedBatchWithMoreRepairCandidates() {
        List<Long> firstCandidates = List.of(30L, 20L);
        List<Long> expandedCandidates = List.of(30L, 20L, 10L);
        when(jobRepository.probeFailedDeletes("POST", 4)).thenReturn(2);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("POST"), eq("POST"),
                any(SqlArrayValue.class), eq(2))).thenReturn(firstCandidates);
        repairs("POST", 3, expandedCandidates, 3, expandedCandidates);
        when(jobRepository.redriveUnlockedDeletes("POST", 4)).thenReturn(1);

        assertThat(backfillService.enqueueBackfill("POST", 4)).isEqualTo(4);

        var order = inOrder(jdbcTemplate, jobRepository);
        order.verify(jobRepository).reserveJobKeys("POST", firstCandidates);
        order.verify(jobRepository).redriveUnlockedDeletes("POST", 4);
        order.verify(jdbcTemplate).execute(startsWith("ROLLBACK TO SAVEPOINT "));
        order.verify(jobRepository).reserveJobKeys("POST", expandedCandidates);
        order.verify(jobRepository).redriveUnlockedDeletes("POST", 4);
        verify(jobRepository).enqueueAll("POST", expandedCandidates, SemanticSearchIndexAction.UPSERT);
        verify(jobRepository, never()).enqueueAll("POST", firstCandidates, SemanticSearchIndexAction.UPSERT);
    }

    @Test
    void newlyFailedDeletesCanBeRedrivenEvenWhenNoRepairsWereFound() {
        when(jobRepository.redriveUnlockedDeletes("POST", 2)).thenReturn(1);

        assertThat(backfillService.enqueueBackfill("POST", 2)).isOne();

        verify(jobRepository, never()).reserveJobKeys(anyString(), anyList());
        verify(jobRepository, never()).enqueueAll(anyString(), anyList(), any());
    }

    @Test
    void deletesThatFillTheLimitAfterRepairSelectionReleaseAllNewRepairReservations() {
        List<Long> candidates = List.of(7L, 3L);
        when(jdbcTemplate.queryForList(anyString(), eq(Long.class), eq("POST"), eq("POST"),
                any(SqlArrayValue.class), eq(2))).thenReturn(candidates);
        when(jobRepository.reserveJobKeys("POST", candidates)).thenReturn(List.of(3L));
        when(jobRepository.redriveUnlockedDeletes("POST", 2)).thenReturn(2);

        assertThat(backfillService.enqueueBackfill("POST", 2)).isEqualTo(2);

        verify(jobRepository, never()).enqueueAll(anyString(), anyList(), any());
        verify(jobRepository).discardUnusedReservations("POST", List.of(3L));
    }

    @Test
    void enqueueBackfillPrioritizesFailedDeletesWithinTheRequestedLimit() {
        when(jobRepository.probeFailedDeletes("POST", 2)).thenReturn(2);
        assertThat(backfillService.enqueueBackfill("POST", 2)).isEqualTo(2);
        verifyNoInteractions(jdbcTemplate);
        verify(jobRepository).probeFailedDeletes("POST", 2);
        verifyNoMoreInteractions(jobRepository);
    }

    @Test
    void lockedPostDeleteLeavesRoomForAPostRepairAndTheCommentTypeKeepsItsOwnLimit() {
        repairs("POST", 1, List.of(1L), 1, List.of(1L));
        when(jobRepository.probeFailedDeletes("POST", 1)).thenReturn(0);
        when(jobRepository.redriveUnlockedDeletes("POST", 1)).thenReturn(0);
        when(jobRepository.probeFailedDeletes("COMMENT", 1)).thenReturn(1);

        assertThat(backfillService.enqueueBackfill("ALL", 1)).isEqualTo(2);

        verify(jobRepository).redriveUnlockedDeletes("POST", 1);
        verify(jobRepository).enqueueAll("POST", List.of(1L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository, never()).enqueueAll(eq("COMMENT"), anyList(), any());
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
        repairs("POST", 10, List.of(1L), 10, List.of(1L));
        repairs("COMMENT", 10, List.of(11L), 10, List.of(11L));
        doThrow(new IllegalStateException("comment enqueue failed"))
                .when(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);

        assertThatThrownBy(() -> backfillService.enqueueBackfill("ALL", 10))
                .isInstanceOf(IllegalStateException.class)
                .hasMessageContaining("comment enqueue failed");

        verify(jobRepository).enqueueAll("POST", List.of(1L), SemanticSearchIndexAction.UPSERT);
        verify(jobRepository).enqueueAll("COMMENT", List.of(11L), SemanticSearchIndexAction.UPSERT);
    }
}
