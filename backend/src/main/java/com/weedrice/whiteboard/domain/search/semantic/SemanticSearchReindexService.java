package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.Duration;
import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class SemanticSearchReindexService {
    private static final int PAGE_SIZE = 500;
    private static final int JOB_BATCH_SIZE = 20;
    private static final int MAX_RETRY_COUNT = 5;
    private static final int MAX_ERROR_LENGTH = 500;
    private static final Duration PROCESSING_LEASE = Duration.ofMinutes(5);
    private static final String STALE_LEASE_ERROR = "Processing lease expired";
    private final SemanticSearchReindexRepository reindexRepository;
    private final SemanticSearchJobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SemanticSearchReindexMetrics metrics;

    void enqueuePostComments(Long postId) { reindexRepository.enqueue("POST_COMMENTS", postId); }
    void enqueueBoard(Long boardId) { reindexRepository.enqueue("BOARD", boardId); }

    int processPages() {
        LocalDateTime current = now();
        int recovered = reindexRepository.recoverStale(
                current.minus(PROCESSING_LEASE),
                MAX_RETRY_COUNT,
                current.plus(backoffFor(0)),
                current,
                STALE_LEASE_ERROR);
        if (recovered > 0) {
            metrics.recordRecovered(recovered);
            log.warn("Recovered {} stale semantic reindex job(s)", recovered);
        }

        int processed = 0;
        for (Long id : reindexRepository.findRunnableIds(now(), JOB_BATCH_SIZE)) {
            SemanticSearchReindexRepository.ReindexCursor claimed;
            try {
                claimed = transactionTemplate.execute(status -> claim(id));
            } catch (RuntimeException ex) {
                metrics.recordClaimError();
                log.error("Failed to claim semantic reindex job: jobId={}", id, ex);
                continue;
            }
            if (claimed == null) {
                continue;
            }

            try {
                Boolean result = transactionTemplate.execute(status -> processPage(claimed));
                if (Boolean.TRUE.equals(result)) {
                    processed++;
                    metrics.recordSuccess();
                }
            } catch (RuntimeException ex) {
                handlePageFailure(claimed, ex);
            }
        }
        refreshMetrics();
        return processed;
    }

    @Transactional
    public void redrive(Long jobId) {
        if (jobId == null || jobId <= 0 || reindexRepository.redrive(jobId, now()) != 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        metrics.recordRedrive();
    }

    private SemanticSearchReindexRepository.ReindexCursor claim(Long id) {
        UUID leaseToken = UUID.randomUUID();
        LocalDateTime claimedAt = now();
        if (reindexRepository.claim(id, leaseToken, claimedAt) != 1) {
            return null;
        }
        return reindexRepository.findClaimed(id, leaseToken);
    }

    private boolean processPage(SemanticSearchReindexRepository.ReindexCursor cursor) {
        List<Long> ids;
        if ("POST".equals(cursor.phase())) {
            ids = jobRepository.findActivePostIdsByBoardIdAfter(
                    cursor.scopeId(), cursor.lastContentId(), PAGE_SIZE);
        } else if ("BOARD".equals(cursor.scopeType())) {
            ids = jobRepository.findActiveCommentIdsByBoardIdAfter(
                    cursor.scopeId(), cursor.lastContentId(), PAGE_SIZE);
        } else {
            ids = jobRepository.findActiveCommentIdsByPostIdAfter(
                    cursor.scopeId(), cursor.lastContentId(), PAGE_SIZE);
        }
        jobRepository.enqueueAll(cursor.phase(), ids, SemanticSearchIndexAction.UPSERT);
        boolean exhausted = ids.size() < PAGE_SIZE;
        int advanced;
        if (exhausted && "BOARD".equals(cursor.scopeType()) && "POST".equals(cursor.phase())) {
            advanced = reindexRepository.advance(
                    cursor.id(), cursor.leaseToken(), "COMMENT", 0, false, now());
        } else {
            long nextCursor = ids.isEmpty() ? cursor.lastContentId() : ids.get(ids.size() - 1);
            advanced = reindexRepository.advance(
                    cursor.id(), cursor.leaseToken(), cursor.phase(), nextCursor, exhausted, now());
        }
        if (advanced != 1) {
            throw new IllegalStateException("Semantic reindex lease was lost");
        }
        return true;
    }

    private void handlePageFailure(SemanticSearchReindexRepository.ReindexCursor cursor, RuntimeException ex) {
        int nextRetryCount = cursor.retryCount() + 1;
        LocalDateTime failedAt = now();
        try {
            Integer updated = transactionTemplate.execute(status -> reindexRepository.markFailed(
                    cursor.id(),
                    cursor.leaseToken(),
                    MAX_RETRY_COUNT,
                    failedAt.plus(backoffFor(cursor.retryCount())),
                    failedAt,
                    summarizeError(ex)));
            if (updated != null && updated == 1) {
                if (nextRetryCount >= MAX_RETRY_COUNT) {
                    metrics.recordDeadLetter();
                } else {
                    metrics.recordRetry();
                }
            }
        } catch (RuntimeException stateError) {
            metrics.recordFailureStateError();
            log.error("Failed to persist semantic reindex failure state: jobId={}", cursor.id(), stateError);
        }
        log.error("Semantic reindex page failed: jobId={}, scopeType={}, scopeId={}, retryCount={}",
                cursor.id(), cursor.scopeType(), cursor.scopeId(), nextRetryCount, ex);
    }

    private void refreshMetrics() {
        LocalDateTime observedAt = now();
        LocalDateTime oldest = reindexRepository.findOldestActiveAt();
        metrics.update(
                reindexRepository.countStatus("PENDING"),
                reindexRepository.countStatus("PROCESSING"),
                reindexRepository.countStatus("FAILED"),
                oldest == null ? 0 : Duration.between(oldest, observedAt).getSeconds());
    }

    private Duration backoffFor(int currentRetryCount) {
        long minutes = Math.min(60, 1L << Math.min(currentRetryCount, 6));
        return Duration.ofMinutes(minutes);
    }

    private String summarizeError(RuntimeException ex) {
        String message = ex.getClass().getSimpleName();
        if (ex.getMessage() != null && !ex.getMessage().isBlank()) {
            message += ": " + ex.getMessage();
        }
        if (message.codePointCount(0, message.length()) <= MAX_ERROR_LENGTH) {
            return message;
        }
        return message.substring(0, message.offsetByCodePoints(0, MAX_ERROR_LENGTH));
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
