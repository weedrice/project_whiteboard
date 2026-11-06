package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

@Repository
@RequiredArgsConstructor
class SemanticSearchJobRepository {
    private static final RowMapper<SemanticSearchJob> JOB_ROW_MAPPER = (rs, rowNum) -> new SemanticSearchJob(
            rs.getLong("job_id"),
            rs.getString("content_type"),
            rs.getLong("content_id"),
            rs.getString("action"),
            toLocalDateTime(rs.getTimestamp("processing_started_at")),
            rs.getInt("retry_count"));

    private final JdbcTemplate jdbcTemplate;
    private final Clock clock;

    void enqueue(String contentType, Long contentId, SemanticSearchIndexAction action) {
        LocalDateTime now = now();
        jdbcTemplate.update("""
                INSERT INTO semantic_search_jobs (
                    content_type, content_id, action, status, retry_count, created_at, updated_at, queued_at, next_attempt_at
                )
                VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                ON CONFLICT (content_type, content_id)
                DO UPDATE SET
                    action = EXCLUDED.action,
                    status = 'PENDING',
                    retry_count = 0,
                    processing_started_at = NULL,
                    completed_at = NULL,
                    last_error_message = NULL,
                    updated_at = EXCLUDED.updated_at,
                    queued_at = EXCLUDED.queued_at,
                    next_attempt_at = EXCLUDED.next_attempt_at
                """, contentType, contentId, action.name(), now, now, now, now);
    }

    void enqueueAll(String contentType, Collection<Long> contentIds, SemanticSearchIndexAction action) {
        if (contentIds == null || contentIds.isEmpty()) {
            return;
        }
        LocalDateTime now = now();
        jdbcTemplate.batchUpdate("""
                INSERT INTO semantic_search_jobs (
                    content_type, content_id, action, status, retry_count, created_at, updated_at, queued_at, next_attempt_at
                )
                VALUES (?, ?, ?, 'PENDING', 0, ?, ?, ?, ?)
                ON CONFLICT (content_type, content_id)
                DO UPDATE SET
                    action = EXCLUDED.action,
                    status = 'PENDING',
                    retry_count = 0,
                    processing_started_at = NULL,
                    completed_at = NULL,
                    last_error_message = NULL,
                    updated_at = EXCLUDED.updated_at,
                    queued_at = EXCLUDED.queued_at,
                    next_attempt_at = EXCLUDED.next_attempt_at
                """, contentIds.stream()
                .map(contentId -> new Object[]{contentType, contentId, action.name(), now, now, now, now})
                .toList());
    }

    List<Long> findActivePostIdsByBoardIdAfter(Long boardId, Long lastSeenPostId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT p.post_id
                FROM posts p
                WHERE p.board_id = ?
                  AND p.is_deleted = 'N'
                  AND p.post_id > ?
                ORDER BY p.post_id ASC
                LIMIT ?
                """, Long.class, boardId, lastSeenPostId, limit);
    }

    List<Long> findActiveCommentIdsByPostIdAfter(Long postId, Long lastSeenCommentId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT c.comment_id
                FROM comments c
                JOIN posts p ON p.post_id = c.post_id
                WHERE c.post_id = ?
                  AND c.is_deleted = 'N'
                  AND p.is_deleted = 'N'
                  AND c.comment_id > ?
                ORDER BY c.comment_id ASC
                LIMIT ?
                """, Long.class, postId, lastSeenCommentId, limit);
    }

    List<Long> findActiveCommentIdsByBoardIdAfter(Long boardId, Long lastSeenCommentId, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT c.comment_id
                FROM comments c
                JOIN posts p ON p.post_id = c.post_id
                WHERE p.board_id = ?
                  AND c.is_deleted = 'N'
                  AND p.is_deleted = 'N'
                  AND c.comment_id > ?
                ORDER BY c.comment_id ASC
                LIMIT ?
                """, Long.class, boardId, lastSeenCommentId, limit);
    }

    int recoverStaleProcessingJobs(LocalDateTime staleBefore, int maxRetryCount, String lastErrorMessage) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET retry_count = retry_count + 1,
                    queued_at = COALESCE(queued_at, processing_started_at, updated_at, created_at),
                    next_attempt_at = ?,
                    status = CASE WHEN retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    processing_started_at = NULL,
                    last_error_message = ?,
                    updated_at = ?
                WHERE status = 'PROCESSING'
                  AND retry_count < ?
                  AND (processing_started_at IS NULL OR processing_started_at < ?)
                """, now().plusMinutes(1), maxRetryCount, lastErrorMessage, now(), maxRetryCount, staleBefore);
    }

    List<SemanticSearchJob> findPendingJobs(int maxRetryCount, int batchSize) {
        return jdbcTemplate.query("""
                SELECT job_id, content_type, content_id, action, processing_started_at, retry_count
                FROM semantic_search_jobs
                WHERE status = 'PENDING'
                  AND retry_count < ?
                  AND (retry_count = 0 OR next_attempt_at IS NULL OR next_attempt_at <= ?)
                ORDER BY CASE WHEN retry_count = 0 THEN updated_at ELSE COALESCE(queued_at, updated_at, created_at) END ASC, job_id ASC
                LIMIT ?
                """, JOB_ROW_MAPPER, maxRetryCount, now(), batchSize);
    }

    List<SemanticSearchJob> findPendingDeletionJobs(int maxRetryCount, int batchSize) {
        return jdbcTemplate.query("""
                SELECT job_id, content_type, content_id, action, processing_started_at, retry_count
                FROM semantic_search_jobs
                WHERE status = 'PENDING' AND action = 'DELETE' AND retry_count < ?
                  AND (retry_count = 0 OR next_attempt_at IS NULL OR next_attempt_at <= ?)
                ORDER BY CASE WHEN retry_count = 0 THEN updated_at ELSE COALESCE(queued_at, updated_at, created_at) END ASC, job_id ASC
                LIMIT ?
                """, JOB_ROW_MAPPER, maxRetryCount, now(), batchSize);
    }

    int claimForProcessing(Long jobId, int maxRetryCount, LocalDateTime claimedAt) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET status = 'PROCESSING',
                    queued_at = CASE WHEN retry_count = 0 THEN updated_at ELSE COALESCE(queued_at, updated_at, created_at) END,
                    processing_started_at = ?,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PENDING'
                  AND retry_count < ?
                    AND (retry_count = 0 OR next_attempt_at IS NULL OR next_attempt_at <= ?)
                """, claimedAt, now(), jobId, maxRetryCount, claimedAt);
    }

    Optional<SemanticSearchJob> findClaimed(Long jobId, LocalDateTime claimedAt) {
        List<SemanticSearchJob> jobs = jdbcTemplate.query("""
                SELECT job_id, content_type, content_id, action, processing_started_at, retry_count
                FROM semantic_search_jobs
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND processing_started_at = ?
                """, JOB_ROW_MAPPER, jobId, claimedAt);
        return jobs.stream().findFirst();
    }

    int releaseClaim(Long jobId, LocalDateTime claimedAt) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET status = 'PENDING', processing_started_at = NULL, updated_at = ?
                WHERE job_id = ? AND status = 'PROCESSING' AND processing_started_at = ?
                """, now(), jobId, claimedAt);
    }

    int markCompleted(Long jobId, LocalDateTime expectedProcessingStartedAt, LocalDateTime completedAt) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET status = 'COMPLETED',
                    processing_started_at = NULL,
                    completed_at = ?,
                    next_attempt_at = NULL,
                    last_error_message = NULL,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND processing_started_at = ?
                """, completedAt, now(), jobId, expectedProcessingStartedAt);
    }

    int markFailedIfCurrent(Long jobId, LocalDateTime expectedProcessingStartedAt, int maxRetryCount,
            String lastErrorMessage, boolean retryable, LocalDateTime nextAttemptAt) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET retry_count = LEAST(retry_count + 1, ?),
                    status = CASE WHEN NOT ? OR retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    processing_started_at = NULL,
                    last_error_message = ?,
                    queued_at = COALESCE(queued_at, processing_started_at, updated_at, created_at),
                    next_attempt_at = CASE WHEN NOT ? OR retry_count + 1 >= ? THEN NULL ELSE ? END,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND processing_started_at = ?
                """, maxRetryCount, retryable, maxRetryCount, lastErrorMessage,
                retryable, maxRetryCount, nextAttemptAt, now(), jobId, expectedProcessingStartedAt);
    }

    long countPendingJobs() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM semantic_search_jobs WHERE status = 'PENDING'",
                Long.class);
        return count == null ? 0L : count;
    }

    int redriveFailedDeletes(String contentType, int limit) {
        return jdbcTemplate.update("""
                WITH selected AS (
                    SELECT job_id FROM semantic_search_jobs
                    WHERE content_type = ? AND action = 'DELETE' AND status = 'FAILED'
                    ORDER BY updated_at ASC, job_id ASC
                    LIMIT ? FOR UPDATE SKIP LOCKED
                )
                UPDATE semantic_search_jobs j
                SET status = 'PENDING', retry_count = 0, processing_started_at = NULL,
                    completed_at = NULL, last_error_message = NULL, queued_at = ?, next_attempt_at = ?, updated_at = ?
                FROM selected WHERE j.job_id = selected.job_id
                  AND j.status = 'FAILED' AND j.action = 'DELETE'
                """, contentType, limit, now(), now(), now());
    }

    long countFailedJobs() {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM semantic_search_jobs WHERE status = 'FAILED'", Long.class);
        return count == null ? 0L : count;
    }

    Optional<LocalDateTime> findOldestPendingCreatedAt() {
        List<LocalDateTime> results = jdbcTemplate.query(
                "SELECT MIN(CASE WHEN retry_count = 0 THEN updated_at ELSE COALESCE(queued_at, updated_at, created_at) END) AS oldest_created_at FROM semantic_search_jobs WHERE status = 'PENDING'",
                (rs, rowNum) -> toLocalDateTime(rs.getTimestamp("oldest_created_at")));
        return results.stream().filter(java.util.Objects::nonNull).findFirst();
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
