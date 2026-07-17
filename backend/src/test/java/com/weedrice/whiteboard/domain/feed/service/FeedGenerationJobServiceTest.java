package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.feed.FeedGenerationJobPolicy;
import com.weedrice.whiteboard.domain.feed.entity.FeedGenerationJob;
import com.weedrice.whiteboard.domain.feed.repository.FeedGenerationJobRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.dao.DataAccessResourceFailureException;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.contains;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FeedGenerationJobServiceTest {

    @Mock
    private FeedGenerationJobRepository feedGenerationJobRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private FeedGenerationService feedGenerationService;
    @Mock
    private FeedGenerationJobMetrics metrics;

    private FeedGenerationJobService feedGenerationJobService;
    private LocalDateTime now;

    @BeforeEach
    void setUp() {
        Clock clock = Clock.fixed(Instant.parse("2026-05-11T00:00:00Z"), ZoneOffset.UTC);
        now = LocalDateTime.of(2026, 5, 11, 0, 0);
        feedGenerationJobService = new FeedGenerationJobService(
                feedGenerationJobRepository,
                boardRepository,
                feedGenerationService,
                clock,
                metrics);
    }

    @Test
    void existsPostPublishedJob_delegatesToRepository() {
        when(feedGenerationJobRepository.existsByPostId(100L)).thenReturn(true);

        boolean exists = feedGenerationJobService.existsPostPublishedJob(100L);

        assertThat(exists).isTrue();
        verify(feedGenerationJobRepository).existsByPostId(100L);
    }

    @Test
    void enqueuePostPublishedJob_savesPendingJobWhenAbsent() {
        when(feedGenerationJobRepository.insertIgnore(100L, 10L)).thenReturn(1);

        feedGenerationJobService.enqueuePostPublishedJob(100L, 10L);

        verify(feedGenerationJobRepository).insertIgnore(100L, 10L);
    }

    @Test
    void enqueuePostPublishedJob_skipsExistingPostJob() {
        when(feedGenerationJobRepository.insertIgnore(100L, 10L)).thenReturn(0);

        feedGenerationJobService.enqueuePostPublishedJob(100L, 10L);

        verify(feedGenerationJobRepository).insertIgnore(100L, 10L);
    }

    @Test
    void enqueuePostPublishedJobPropagatesRealStorageFailure() {
        DataAccessResourceFailureException failure = new DataAccessResourceFailureException("storage unavailable");
        when(feedGenerationJobRepository.insertIgnore(100L, 10L)).thenThrow(failure);

        assertThatThrownBy(() -> feedGenerationJobService.enqueuePostPublishedJob(100L, 10L))
                .isSameAs(failure);
    }

    @Test
    void enqueuePostPublishedJobJoinsThePublishingTransaction() throws Exception {
        Method method = FeedGenerationJobService.class
                .getMethod("enqueuePostPublishedJob", Long.class, Long.class);
        Transactional transactional = method.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.propagation()).isEqualTo(Propagation.REQUIRED);
    }

    @Test
    void processPostPublishedJob_claimsAndCompletesJob() {
        FeedGenerationJob job = processingJob(1L, 100L, 10L, now);
        Board board = board(10L);

        when(feedGenerationJobRepository.claimForProcessingByPostId(
                100L, FeedGenerationJobPolicy.MAX_RETRY_COUNT, now)).thenReturn(1);
        when(feedGenerationJobRepository.findByPostIdAndStatusAndProcessingStartedAt(
                100L, FeedGenerationJob.STATUS_PROCESSING, now)).thenReturn(Optional.of(job));
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(feedGenerationJobRepository.markCompleted(1L, now, now)).thenReturn(1);

        feedGenerationJobService.processPostPublishedJob(100L);

        verify(feedGenerationService).generatePostFeeds(board, 100L);
        verify(feedGenerationJobRepository).markCompleted(1L, now, now);
    }

    @Test
    void processPostPublishedJob_skipsCompletionWhenLeaseChanged() {
        FeedGenerationJob job = processingJob(1L, 100L, 10L, now);
        Board board = board(10L);

        when(feedGenerationJobRepository.claimForProcessingByPostId(
                100L, FeedGenerationJobPolicy.MAX_RETRY_COUNT, now)).thenReturn(1);
        when(feedGenerationJobRepository.findByPostIdAndStatusAndProcessingStartedAt(
                100L, FeedGenerationJob.STATUS_PROCESSING, now)).thenReturn(Optional.of(job));
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(feedGenerationJobRepository.markCompleted(1L, now, now)).thenReturn(0);

        feedGenerationJobService.processPostPublishedJob(100L);

        verify(feedGenerationService).generatePostFeeds(board, 100L);
        verify(feedGenerationJobRepository, never()).markFailedIfCurrent(
                anyLong(),
                any(),
                anyInt(),
                any(),
                anyString());
    }

    @Test
    void processPostPublishedJob_marksFailureForRetryWhenGenerationFails() {
        FeedGenerationJob job = processingJob(1L, 100L, 10L, now);
        Board board = board(10L);

        when(feedGenerationJobRepository.claimForProcessingByPostId(
                100L, FeedGenerationJobPolicy.MAX_RETRY_COUNT, now)).thenReturn(1);
        when(feedGenerationJobRepository.findByPostIdAndStatusAndProcessingStartedAt(
                100L, FeedGenerationJob.STATUS_PROCESSING, now)).thenReturn(Optional.of(job));
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        doThrow(new IllegalStateException("boom"))
                .when(feedGenerationService)
                .generatePostFeeds(board, 100L);
        when(feedGenerationJobRepository.markFailedIfCurrent(
                eq(1L),
                eq(now),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                eq(now.plusMinutes(1)),
                anyString())).thenReturn(1);

        feedGenerationJobService.processPostPublishedJob(100L);

        verify(feedGenerationJobRepository).markFailedIfCurrent(
                eq(1L),
                eq(now),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                eq(now.plusMinutes(1)),
                contains("IllegalStateException: boom"));
    }

    @Test
    void processPendingJobs_recoversStaleJobsAndUsesStableBatchOrder() {
        FeedGenerationJob job = processingJob(1L, 100L, 10L, now);
        Board board = board(10L);
        when(feedGenerationJobRepository.recoverStaleProcessingJobs(
                now.minusMinutes(FeedGenerationJobPolicy.PROCESSING_LEASE_MINUTES),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                now.plusMinutes(1),
                "Processing lease expired")).thenReturn(1);
        when(feedGenerationJobRepository.findJobIdsByStatusAndRetryCountLessThan(
                eq(FeedGenerationJob.STATUS_PENDING),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                eq(now),
                any(Pageable.class))).thenReturn(List.of(jobIdProjection(1L)));
        when(feedGenerationJobRepository.claimForProcessing(
                1L, FeedGenerationJobPolicy.MAX_RETRY_COUNT, now)).thenReturn(1);
        when(feedGenerationJobRepository.findByJobIdAndStatusAndProcessingStartedAt(
                1L, FeedGenerationJob.STATUS_PROCESSING, now)).thenReturn(Optional.of(job));
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(feedGenerationJobRepository.markCompleted(1L, now, now)).thenReturn(1);

        int processedCount = feedGenerationJobService.processPendingJobs();

        assertThat(processedCount).isEqualTo(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(feedGenerationJobRepository).findJobIdsByStatusAndRetryCountLessThan(
                eq(FeedGenerationJob.STATUS_PENDING),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                eq(now),
                pageableCaptor.capture());
        Pageable pageable = pageableCaptor.getValue();
        assertThat(pageable.getPageSize()).isEqualTo(FeedGenerationJobPolicy.BATCH_SIZE);
        assertThat(pageable.getSort().stream().map(order -> order.getProperty()).toList())
                .containsExactly("createdAt", "jobId");
        assertThat(pageable.getSort().getOrderFor("createdAt").isAscending()).isTrue();
        assertThat(pageable.getSort().getOrderFor("jobId").isAscending()).isTrue();
        verify(feedGenerationService).generatePostFeeds(board, 100L);
    }

    @Test
    void processPendingJobs_skipsUnclaimedJob() {
        when(feedGenerationJobRepository.recoverStaleProcessingJobs(
                now.minusMinutes(FeedGenerationJobPolicy.PROCESSING_LEASE_MINUTES),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                now.plusMinutes(1),
                "Processing lease expired")).thenReturn(0);
        when(feedGenerationJobRepository.findJobIdsByStatusAndRetryCountLessThan(
                eq(FeedGenerationJob.STATUS_PENDING),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                eq(now),
                any(Pageable.class))).thenReturn(List.of(jobIdProjection(1L)));
        when(feedGenerationJobRepository.claimForProcessing(
                1L, FeedGenerationJobPolicy.MAX_RETRY_COUNT, now)).thenReturn(0);

        int processedCount = feedGenerationJobService.processPendingJobs();

        assertThat(processedCount).isZero();
        verify(feedGenerationService, never()).generatePostFeeds(
                any(Board.class),
                anyLong());
    }

    @Test
    void redriveResetsOnlyFailedJobAndRefreshesMetrics() {
        when(feedGenerationJobRepository.redriveFailed(7L, now)).thenReturn(1);

        feedGenerationJobService.redrive(7L);

        verify(feedGenerationJobRepository).redriveFailed(7L, now);
        verify(metrics).recordRedrive();
        verify(metrics).update(0L, 0L, 0L, 0L);
    }

    @Test
    void redriveRejectsMissingOrNonFailedJob() {
        when(feedGenerationJobRepository.redriveFailed(7L, now)).thenReturn(0);

        assertThatThrownBy(() -> feedGenerationJobService.redrive(7L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(metrics, never()).recordRedrive();
    }

    private FeedGenerationJob processingJob(Long jobId, Long postId, Long boardId, LocalDateTime claimedAt) {
        FeedGenerationJob job = FeedGenerationJob.builder()
                .postId(postId)
                .boardId(boardId)
                .build();
        ReflectionTestUtils.setField(job, "jobId", jobId);
        ReflectionTestUtils.setField(job, "status", FeedGenerationJob.STATUS_PROCESSING);
        ReflectionTestUtils.setField(job, "processingStartedAt", claimedAt);
        return job;
    }

    private FeedGenerationJobRepository.JobIdProjection jobIdProjection(Long jobId) {
        return () -> jobId;
    }

    private Board board(Long boardId) {
        Board board = Board.builder().boardName("free").build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        return board;
    }
}
