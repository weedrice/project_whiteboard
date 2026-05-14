package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.feed.FeedGenerationJobPolicy;
import com.weedrice.whiteboard.domain.feed.entity.FeedGenerationJob;
import com.weedrice.whiteboard.domain.feed.repository.FeedGenerationJobRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Pageable;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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
                clock);
    }

    @Test
    void enqueuePostPublishedJob_savesPendingJobWhenAbsent() {
        when(feedGenerationJobRepository.existsByPostId(100L)).thenReturn(false);

        feedGenerationJobService.enqueuePostPublishedJob(100L, 10L);

        ArgumentCaptor<FeedGenerationJob> jobCaptor = ArgumentCaptor.forClass(FeedGenerationJob.class);
        verify(feedGenerationJobRepository).save(jobCaptor.capture());
        assertThat(jobCaptor.getValue().getPostId()).isEqualTo(100L);
        assertThat(jobCaptor.getValue().getBoardId()).isEqualTo(10L);
        assertThat(jobCaptor.getValue().getStatus()).isEqualTo(FeedGenerationJob.STATUS_PENDING);
        assertThat(jobCaptor.getValue().getRetryCount()).isZero();
    }

    @Test
    void enqueuePostPublishedJob_skipsExistingPostJob() {
        when(feedGenerationJobRepository.existsByPostId(100L)).thenReturn(true);

        feedGenerationJobService.enqueuePostPublishedJob(100L, 10L);

        verify(feedGenerationJobRepository, never()).save(any());
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
                anyString())).thenReturn(1);

        feedGenerationJobService.processPostPublishedJob(100L);

        verify(feedGenerationJobRepository).markFailedIfCurrent(
                eq(1L),
                eq(now),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                contains("IllegalStateException: boom"));
    }

    @Test
    void processPendingJobs_recoversStaleJobsAndUsesStableBatchOrder() {
        FeedGenerationJob job = processingJob(1L, 100L, 10L, now);
        Board board = board(10L);
        when(feedGenerationJobRepository.recoverStaleProcessingJobs(
                now.minusMinutes(FeedGenerationJobPolicy.PROCESSING_LEASE_MINUTES),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                "Processing lease expired")).thenReturn(1);
        when(feedGenerationJobRepository.findByStatusAndRetryCountLessThan(
                eq(FeedGenerationJob.STATUS_PENDING),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                any(Pageable.class))).thenReturn(List.of(job));
        when(feedGenerationJobRepository.claimForProcessing(
                1L, FeedGenerationJobPolicy.MAX_RETRY_COUNT, now)).thenReturn(1);
        when(feedGenerationJobRepository.findByJobIdAndStatusAndProcessingStartedAt(
                1L, FeedGenerationJob.STATUS_PROCESSING, now)).thenReturn(Optional.of(job));
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(feedGenerationJobRepository.markCompleted(1L, now, now)).thenReturn(1);

        int processedCount = feedGenerationJobService.processPendingJobs();

        assertThat(processedCount).isEqualTo(1);
        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(feedGenerationJobRepository).findByStatusAndRetryCountLessThan(
                eq(FeedGenerationJob.STATUS_PENDING),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
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
        FeedGenerationJob job = processingJob(1L, 100L, 10L, now);
        when(feedGenerationJobRepository.recoverStaleProcessingJobs(
                now.minusMinutes(FeedGenerationJobPolicy.PROCESSING_LEASE_MINUTES),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                "Processing lease expired")).thenReturn(0);
        when(feedGenerationJobRepository.findByStatusAndRetryCountLessThan(
                eq(FeedGenerationJob.STATUS_PENDING),
                eq(FeedGenerationJobPolicy.MAX_RETRY_COUNT),
                any(Pageable.class))).thenReturn(List.of(job));
        when(feedGenerationJobRepository.claimForProcessing(
                1L, FeedGenerationJobPolicy.MAX_RETRY_COUNT, now)).thenReturn(0);

        int processedCount = feedGenerationJobService.processPendingJobs();

        assertThat(processedCount).isZero();
        verify(feedGenerationService, never()).generatePostFeeds(
                any(Board.class),
                anyLong());
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

    private Board board(Long boardId) {
        Board board = Board.builder().boardName("free").build();
        ReflectionTestUtils.setField(board, "boardId", boardId);
        return board;
    }
}
