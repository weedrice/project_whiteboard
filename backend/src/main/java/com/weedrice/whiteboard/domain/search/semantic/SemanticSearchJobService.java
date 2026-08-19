package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Collection;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchJobService {
    private static final int MAX_ERROR_MESSAGE_LENGTH = 500;
    private static final int REINDEX_ENQUEUE_CHUNK_SIZE = 500;
    private static final String STALE_PROCESSING_ERROR = "Processing lease expired";

    private final SemanticSearchProperties properties;
    private final EmbeddingClient embeddingClient;
    private final SemanticSearchJobRepository jobRepository;
    private final SemanticSearchJobCommandService jobCommandService;
    private final SemanticSearchIndexService indexService;
    private final Clock clock;
    private final SemanticSearchReindexService reindexService;

    @Transactional
    public void enqueue(String contentType, Long contentId, SemanticSearchIndexAction action) {
        jobRepository.enqueue(contentType, contentId, action);
    }

    @Transactional
    public void enqueueAll(String contentType, Collection<Long> contentIds, SemanticSearchIndexAction action) {
        jobRepository.enqueueAll(contentType, contentIds, action);
    }

    @Transactional
    public int enqueuePostComments(Long postId) {
        reindexService.enqueuePostComments(postId);
        return 1;
    }

    @Transactional
    public int enqueueBoardContent(Long boardId) {
        reindexService.enqueueBoard(boardId);
        return 1;
    }

    public int processPendingJobs() {
        if (!properties.isEnabled() || !embeddingClient.isAvailable()) {
            return 0;
        }
        reindexService.processPages();

        LocalDateTime current = now();
        int recovered = jobCommandService.recoverStaleProcessingJobs(
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
            int claimed = jobCommandService.claimForProcessing(
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
        jobCommandService.findClaimed(jobId, claimedAt)
                .ifPresent(job -> processClaimedJob(job, claimedAt));
    }

    private void processClaimedJob(SemanticSearchJob job, LocalDateTime claimedAt) {
        try {
            processIndexOperation(job);
            jobCommandService.markCompleted(job.jobId(), claimedAt, now());
        } catch (Exception ex) {
            jobCommandService.markFailedIfCurrent(
                    job.jobId(),
                    claimedAt,
                    properties.getJob().getMaxRetryCount(),
                    summarizeError(ex));
            log.error("Semantic search job failed: jobId={}, contentType={}, contentId={}",
                    job.jobId(), job.contentType(), job.contentId(), ex);
        }
    }

    private void processIndexOperation(SemanticSearchJob job) {
        SemanticSearchIndexAction action = parseAction(job.action());
        SemanticSearchContentType contentType = parseJobContentType(job.contentType());

        switch (action) {
            case DELETE -> indexService.delete(contentType.name(), job.contentId());
            case UPSERT -> {
                switch (contentType) {
                    case POST -> indexService.upsertPost(job.contentId());
                    case COMMENT -> indexService.upsertComment(job.contentId());
                    case ALL -> throw new IllegalStateException("ALL is not a valid semantic search job content type");
                }
            }
        }
    }

    private SemanticSearchIndexAction parseAction(String action) {
        try {
            return SemanticSearchIndexAction.valueOf(action);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException("Unsupported semantic search job action: " + action, ex);
        }
    }

    private SemanticSearchContentType parseJobContentType(String contentType) {
        final SemanticSearchContentType parsed;
        try {
            parsed = SemanticSearchContentType.valueOf(contentType);
        } catch (IllegalArgumentException | NullPointerException ex) {
            throw new IllegalStateException("Unsupported semantic search job content type: " + contentType, ex);
        }
        if (parsed == SemanticSearchContentType.ALL) {
            throw new IllegalStateException("ALL is not a valid semantic search job content type");
        }
        return parsed;
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
