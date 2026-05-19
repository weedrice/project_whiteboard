package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchJobService {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final String STALE_PROCESSING_ERROR = "Processing lease expired";

    private final SemanticSearchProperties properties;
    private final EmbeddingClient embeddingClient;
    private final SemanticSearchJobRepository jobRepository;
    private final SemanticSearchIndexService indexService;
    private final Clock clock;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void enqueue(String contentType, Long contentId, SemanticSearchIndexAction action) {
        jobRepository.enqueue(contentType, contentId, action);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int enqueuePostComments(Long postId) {
        List<Long> commentIds = jobRepository.findActiveCommentIdsByPostId(postId);
        commentIds.forEach(commentId -> jobRepository.enqueue("COMMENT", commentId, SemanticSearchIndexAction.UPSERT));
        return commentIds.size();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int enqueueBoardContent(Long boardId) {
        List<Long> postIds = jobRepository.findActivePostIdsByBoardId(boardId);
        List<Long> commentIds = jobRepository.findActiveCommentIdsByBoardId(boardId);
        postIds.forEach(postId -> jobRepository.enqueue("POST", postId, SemanticSearchIndexAction.UPSERT));
        commentIds.forEach(commentId -> jobRepository.enqueue("COMMENT", commentId, SemanticSearchIndexAction.UPSERT));
        return postIds.size() + commentIds.size();
    }

    public int processPendingJobs() {
        if (!properties.isEnabled() || !embeddingClient.isAvailable()) {
            return 0;
        }

        LocalDateTime current = now();
        int recovered = jobRepository.recoverStaleProcessingJobs(
                current.minusMinutes(properties.getJob().getProcessingLeaseMinutes()),
                properties.getJob().getMaxRetryCount(),
                STALE_PROCESSING_ERROR);
        if (recovered > 0) {
            log.warn("Recovered {} stale semantic search job(s)", recovered);
        }

        List<SemanticSearchJob> jobs = jobRepository.findPendingJobs(
                properties.getJob().getMaxRetryCount(),
                properties.getJob().getBatchSize());

        int processedCount = 0;
        for (SemanticSearchJob job : jobs) {
            LocalDateTime claimedAt = now();
            int claimed = jobRepository.claimForProcessing(
                    job.jobId(),
                    properties.getJob().getMaxRetryCount(),
                    claimedAt);
            if (claimed == 1) {
                processedCount++;
                processClaimedJob(job.jobId(), claimedAt);
            }
        }
        return processedCount;
    }

    private void processClaimedJob(Long jobId, LocalDateTime claimedAt) {
        jobRepository.findClaimed(jobId, claimedAt)
                .ifPresent(job -> processClaimedJob(job, claimedAt));
    }

    private void processClaimedJob(SemanticSearchJob job, LocalDateTime claimedAt) {
        try {
            if (SemanticSearchIndexAction.DELETE.name().equals(job.action())) {
                indexService.delete(job.contentType(), job.contentId());
            } else if ("POST".equals(job.contentType())) {
                indexService.upsertPost(job.contentId());
            } else if ("COMMENT".equals(job.contentType())) {
                indexService.upsertComment(job.contentId());
            }
            jobRepository.markCompleted(job.jobId(), claimedAt, now());
        } catch (Exception ex) {
            jobRepository.markFailedIfCurrent(
                    job.jobId(),
                    claimedAt,
                    properties.getJob().getMaxRetryCount(),
                    summarizeError(ex));
            log.error("Semantic search job failed: jobId={}, contentType={}, contentId={}",
                    job.jobId(), job.contentType(), job.contentId(), ex);
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
