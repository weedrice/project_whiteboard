package com.weedrice.whiteboard.domain.feed.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.feed.FeedGenerationJobPolicy;
import com.weedrice.whiteboard.domain.feed.entity.FeedGenerationJob;
import com.weedrice.whiteboard.domain.feed.repository.FeedGenerationJobRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FeedGenerationJobService {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String STALE_PROCESSING_ERROR = "Processing lease expired";

    private final FeedGenerationJobRepository feedGenerationJobRepository;
    private final BoardRepository boardRepository;
    private final FeedGenerationService feedGenerationService;
    private final Clock clock;

    @Transactional
    public void enqueuePostPublishedJob(Long postId, Long boardId) {
        if (feedGenerationJobRepository.existsByPostId(postId)) {
            return;
        }
        FeedGenerationJob job = FeedGenerationJob.builder()
                .postId(postId)
                .boardId(boardId)
                .build();
        feedGenerationJobRepository.save(job);
    }

    public void processPostPublishedJob(Long postId) {
        LocalDateTime claimedAt = now();
        int claimed = feedGenerationJobRepository.claimForProcessingByPostId(
                postId,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                claimedAt);
        if (claimed != 1) {
            return;
        }

        feedGenerationJobRepository.findByPostIdAndStatusAndProcessingStartedAt(
                        postId,
                        FeedGenerationJob.STATUS_PROCESSING,
                        claimedAt)
                .ifPresent(job -> processClaimedJob(job, claimedAt));
    }

    public int processPendingJobs() {
        LocalDateTime current = now();
        int recovered = feedGenerationJobRepository.recoverStaleProcessingJobs(
                current.minusMinutes(FeedGenerationJobPolicy.PROCESSING_LEASE_MINUTES),
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                STALE_PROCESSING_ERROR);
        if (recovered > 0) {
            log.warn("Recovered {} stale feed generation job(s)", recovered);
        }

        PageRequest pendingPageRequest = PageRequest.of(
                0,
                FeedGenerationJobPolicy.BATCH_SIZE,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("jobId")));
        List<FeedGenerationJob> pendingJobs = feedGenerationJobRepository.findByStatusAndRetryCountLessThan(
                FeedGenerationJob.STATUS_PENDING,
                FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                pendingPageRequest);

        int processedCount = 0;
        for (FeedGenerationJob job : pendingJobs) {
            LocalDateTime claimedAt = now();
            int claimed = feedGenerationJobRepository.claimForProcessing(
                    job.getJobId(),
                    FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                    claimedAt);
            if (claimed == 1) {
                processedCount++;
                processClaimedJob(job.getJobId(), claimedAt);
            }
        }
        return processedCount;
    }

    private void processClaimedJob(Long jobId, LocalDateTime claimedAt) {
        feedGenerationJobRepository.findByJobIdAndStatusAndProcessingStartedAt(
                        jobId,
                        FeedGenerationJob.STATUS_PROCESSING,
                        claimedAt)
                .ifPresent(job -> processClaimedJob(job, claimedAt));
    }

    private void processClaimedJob(FeedGenerationJob job, LocalDateTime claimedAt) {
        try {
            Board board = boardRepository.findById(job.getBoardId())
                    .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
            feedGenerationService.generatePostFeeds(board, job.getPostId());
            int completed = feedGenerationJobRepository.markCompleted(job.getJobId(), now());
            if (completed != 1) {
                log.warn("Skipped completing feed generation job because it was already completed: jobId={}",
                        job.getJobId());
            }
        } catch (Exception ex) {
            int failed = feedGenerationJobRepository.markFailedIfCurrent(
                    job.getJobId(),
                    claimedAt,
                    FeedGenerationJobPolicy.MAX_RETRY_COUNT,
                    summarizeError(ex));
            if (failed != 1) {
                log.warn("Skipped feed generation failure update because processing lease changed: jobId={}",
                        job.getJobId());
            }
            log.error("Feed generation job failed: jobId={}, postId={}, boardId={}",
                    job.getJobId(),
                    job.getPostId(),
                    job.getBoardId(),
                    ex);
        }
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MICROS);
    }

    private String summarizeError(Exception ex) {
        String message = ex.getClass().getSimpleName();
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message += ": " + ex.getMessage();
        }
        if (message.codePointCount(0, message.length()) <= MAX_ERROR_MESSAGE_LENGTH) {
            return message;
        }
        return message.substring(0, message.offsetByCodePoints(0, MAX_ERROR_MESSAGE_LENGTH));
    }
}
