package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class SemanticSearchReindexService {
    private static final int PAGE_SIZE = 500;
    private final SemanticSearchReindexRepository reindexRepository;
    private final SemanticSearchJobRepository jobRepository;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;
    private final SemanticSearchReindexMetrics metrics;

    void enqueuePostComments(Long postId) { reindexRepository.enqueue("POST_COMMENTS", postId); }
    void enqueueBoard(Long boardId) { reindexRepository.enqueue("BOARD", boardId); }

    int processPages() {
        reindexRepository.recoverStale(now().minusMinutes(5));
        int processed = 0;
        for (Long id : reindexRepository.findPendingIds(20)) {
            Boolean result = transactionTemplate.execute(status -> processPage(id));
            if (Boolean.TRUE.equals(result)) processed++;
        }
        LocalDateTime observedAt = now();
        LocalDateTime oldest = reindexRepository.findOldestActiveAt();
        metrics.update(
                reindexRepository.countStatus("PENDING"),
                reindexRepository.countStatus("PROCESSING"),
                oldest == null ? 0 : java.time.Duration.between(oldest, observedAt).getSeconds());
        return processed;
    }

    private boolean processPage(Long id) {
        if (reindexRepository.claim(id, now()) != 1) return false;
        var cursor = reindexRepository.findClaimed(id);
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
        if (exhausted && "BOARD".equals(cursor.scopeType()) && "POST".equals(cursor.phase())) {
            reindexRepository.advance(id, "COMMENT", 0, false, now());
        } else {
            long nextCursor = ids.isEmpty() ? cursor.lastContentId() : ids.get(ids.size() - 1);
            reindexRepository.advance(id, cursor.phase(), nextCursor, exhausted, now());
        }
        return true;
    }

    private LocalDateTime now() { return LocalDateTime.now(clock); }
}
