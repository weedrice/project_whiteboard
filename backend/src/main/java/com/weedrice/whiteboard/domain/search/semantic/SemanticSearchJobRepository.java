package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
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
            toLocalDateTime(rs.getTimestamp("processing_started_at")));

    private final JdbcTemplate jdbcTemplate;

    void enqueue(String contentType, Long contentId, SemanticSearchIndexAction action) {
        jdbcTemplate.update("""
                INSERT INTO semantic_search_jobs (
                    content_type, content_id, action, status, retry_count, created_at, updated_at
                )
                VALUES (?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (content_type, content_id)
                DO UPDATE SET
                    action = EXCLUDED.action,
                    status = 'PENDING',
                    retry_count = 0,
                    processing_started_at = NULL,
                    completed_at = NULL,
                    last_error_message = NULL,
                    updated_at = EXCLUDED.updated_at
                """, contentType, contentId, action.name(), LocalDateTime.now(), LocalDateTime.now());
    }

    void enqueueAll(String contentType, Collection<Long> contentIds, SemanticSearchIndexAction action) {
        if (contentIds == null || contentIds.isEmpty()) {
            return;
        }
        LocalDateTime now = LocalDateTime.now();
        jdbcTemplate.batchUpdate("""
                INSERT INTO semantic_search_jobs (
                    content_type, content_id, action, status, retry_count, created_at, updated_at
                )
                VALUES (?, ?, ?, 'PENDING', 0, ?, ?)
                ON CONFLICT (content_type, content_id)
                DO UPDATE SET
                    action = EXCLUDED.action,
                    status = 'PENDING',
                    retry_count = 0,
                    processing_started_at = NULL,
                    completed_at = NULL,
                    last_error_message = NULL,
                    updated_at = EXCLUDED.updated_at
                """, contentIds.stream()
                .map(contentId -> new Object[]{contentType, contentId, action.name(), now, now})
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
                    status = CASE WHEN retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    processing_started_at = NULL,
                    last_error_message = ?,
                    updated_at = ?
                WHERE status = 'PROCESSING'
                  AND retry_count < ?
                  AND (processing_started_at IS NULL OR processing_started_at < ?)
                """, maxRetryCount, lastErrorMessage, LocalDateTime.now(), maxRetryCount, staleBefore);
    }

    List<SemanticSearchJob> findPendingJobs(int maxRetryCount, int batchSize) {
        return jdbcTemplate.query("""
                SELECT job_id, content_type, content_id, action, processing_started_at
                FROM semantic_search_jobs
                WHERE status = 'PENDING'
                  AND retry_count < ?
                ORDER BY created_at ASC, job_id ASC
                LIMIT ?
                """, JOB_ROW_MAPPER, maxRetryCount, batchSize);
    }

    int claimForProcessing(Long jobId, int maxRetryCount, LocalDateTime claimedAt) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET status = 'PROCESSING',
                    processing_started_at = ?,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PENDING'
                  AND retry_count < ?
                """, claimedAt, LocalDateTime.now(), jobId, maxRetryCount);
    }

    Optional<SemanticSearchJob> findClaimed(Long jobId, LocalDateTime claimedAt) {
        List<SemanticSearchJob> jobs = jdbcTemplate.query("""
                SELECT job_id, content_type, content_id, action, processing_started_at
                FROM semantic_search_jobs
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND processing_started_at = ?
                """, JOB_ROW_MAPPER, jobId, claimedAt);
        return jobs.stream().findFirst();
    }

    int markCompleted(Long jobId, LocalDateTime expectedProcessingStartedAt, LocalDateTime completedAt) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET status = 'COMPLETED',
                    processing_started_at = NULL,
                    completed_at = ?,
                    last_error_message = NULL,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND processing_started_at = ?
                """, completedAt, LocalDateTime.now(), jobId, expectedProcessingStartedAt);
    }

    int markFailedIfCurrent(Long jobId, LocalDateTime expectedProcessingStartedAt, int maxRetryCount,
            String lastErrorMessage) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_jobs
                SET retry_count = retry_count + 1,
                    status = CASE WHEN retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    processing_started_at = NULL,
                    last_error_message = ?,
                    updated_at = ?
                WHERE job_id = ?
                  AND status = 'PROCESSING'
                  AND processing_started_at = ?
                """, maxRetryCount, lastErrorMessage, LocalDateTime.now(), jobId, expectedProcessingStartedAt);
    }

    private static LocalDateTime toLocalDateTime(Timestamp timestamp) {
        return timestamp == null ? null : timestamp.toLocalDateTime();
    }
}
