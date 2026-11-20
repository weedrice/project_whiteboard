package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.RowMapper;
import org.springframework.jdbc.support.SqlArrayValue;
import org.springframework.stereotype.Repository;

import java.sql.Timestamp;
import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.Set;

@Repository
@RequiredArgsConstructor
class SemanticSearchJobRepository {
    private static final String ENQUEUE_JOB = """
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
            """;

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
        jdbcTemplate.update(ENQUEUE_JOB, contentType, contentId, action.name(), now, now, now, now);
    }

    void enqueueAll(String contentType, Collection<Long> contentIds, SemanticSearchIndexAction action) {
        enqueueAll(ENQUEUE_JOB, contentType, contentIds, action);
    }

    private void enqueueAll(String sql, String contentType, Collection<Long> contentIds, SemanticSearchIndexAction action) {
        if (contentIds == null || contentIds.isEmpty()) {
            return;
        }
        LocalDateTime now = now();
        jdbcTemplate.batchUpdate(sql, contentIds.stream()
                .map(contentId -> new Object[]{contentType, contentId, action.name(), now, now, now, now})
                .toList());
    }

    List<Long> findAuthorPageIds(String phase, Long authorId, long afterId, int limit) {
        if ("POST".equals(phase)) {
            return jdbcTemplate.queryForList("""
                    SELECT post_id FROM posts
                    WHERE user_id = ? AND is_deleted = 'N' AND post_id > ?
                    ORDER BY post_id LIMIT ?
                    """, Long.class, authorId, afterId, limit);
        }
        return jdbcTemplate.queryForList("""
                SELECT comment_id FROM (
                    (SELECT c.comment_id FROM comments c JOIN posts p ON p.post_id = c.post_id
                     WHERE c.user_id = ? AND c.is_deleted = 'N' AND p.is_deleted = 'N' AND c.comment_id > ?
                     ORDER BY c.comment_id LIMIT ?)
                    UNION ALL
                    (SELECT c.comment_id FROM posts p JOIN comments c ON c.post_id = p.post_id
                     WHERE p.user_id = ? AND p.is_deleted = 'N' AND c.is_deleted = 'N'
                       AND c.user_id IS DISTINCT FROM ? AND c.comment_id > ?
                     ORDER BY c.comment_id LIMIT ?)
                ) affected ORDER BY comment_id LIMIT ?
                """, Long.class, authorId, afterId, limit, authorId, authorId, afterId, limit, limit);
    }

    void enqueueReindexPage(String phase, List<Long> ids, boolean deleteHidden) {
        if (ids.isEmpty()) {
            return;
        }
        List<Long> candidates = deleteHidden ? ids : findVisiblePageIds(phase, ids);
        if (candidates.isEmpty()) {
            return;
        }

        // PostgreSQL locks a conflicting row even if its DO UPDATE condition is false.
        // Reserve new and existing job keys in the same order without changing existing jobs or leases.
        reserveJobKeys(phase, candidates);

        // A separate READ COMMITTED statement sees edits that committed while reserving the keys.
        // A writer enqueuing a newer action for the same key waits until this transaction commits.
        List<Long> visible = findVisiblePageIds(phase, candidates);
        enqueueAll(phase, visible, SemanticSearchIndexAction.UPSERT);
        if (deleteHidden) {
            Set<Long> visibleIds = Set.copyOf(visible);
            List<Long> hidden = candidates.stream().filter(id -> !visibleIds.contains(id)).toList();
            String deleteSql = ENQUEUE_JOB + """
                    WHERE semantic_search_jobs.action <> 'DELETE'
                       OR semantic_search_jobs.status NOT IN ('PENDING', 'PROCESSING')
                    """;
            enqueueAll(deleteSql, phase, hidden, SemanticSearchIndexAction.DELETE);
        }
    }

    private List<Long> findVisiblePageIds(String phase, List<Long> ids) {
        String sql = "SELECT content_id FROM (" + SemanticSearchSqlFragments.indexableSources(phase)
                + ") source WHERE content_id = ANY(?) ORDER BY content_id";
        return jdbcTemplate.queryForList(sql, Long.class, new SqlArrayValue("bigint", ids.toArray()));
    }

    // The guard locks existing keys without replacing their action, lease or retry schedule.
    // RETURNING includes only newly inserted reservations. All keys stay locked until commit.
    List<Long> reserveJobKeys(String contentType, Collection<Long> ids) {
        LocalDateTime now = now();
        return jdbcTemplate.queryForList("""
                INSERT INTO semantic_search_jobs (
                    content_type, content_id, action, status, retry_count, created_at, updated_at, queued_at, next_attempt_at
                )
                SELECT ?, candidate_id, 'DELETE', 'PENDING', 0, ?, ?, ?, ?
                FROM unnest(?::bigint[]) AS candidates(candidate_id)
                ORDER BY candidate_id
                ON CONFLICT (content_type, content_id) DO UPDATE SET action = EXCLUDED.action WHERE FALSE
                RETURNING content_id
                """, Long.class, contentType, now, now, now, now, new SqlArrayValue("bigint", ids.toArray()));
    }

    void discardUnusedReservations(String contentType, List<Long> ids) {
        if (!ids.isEmpty()) {
            jdbcTemplate.update("DELETE FROM semantic_search_jobs WHERE content_type=? AND content_id=ANY(?)",
                    contentType, new SqlArrayValue("bigint", ids.toArray()));
        }
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
                    next_attempt_at = CASE WHEN NOT ? OR retry_count + 1 >= ? THEN NULL ELSE CAST(? AS timestamp) END,
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

    private static final String PICK_FAILED_DELETES = """
            SELECT job_id FROM semantic_search_jobs
            WHERE content_type = ? AND action = 'DELETE' AND status = 'FAILED'
            ORDER BY updated_at, job_id LIMIT ? FOR UPDATE SKIP LOCKED
            """;

    private static final String REDRIVE_DELETES = """
            UPDATE semantic_search_jobs SET status='PENDING', retry_count=0, processing_started_at=NULL,
                completed_at=NULL, last_error_message=NULL, queued_at=?, next_attempt_at=?, updated_at=?
            WHERE status='FAILED' AND action='DELETE' AND job_id
            """;

    // Finish a delete-only batch immediately. For a mixed batch, return the number that
    // could be claimed and release the probe locks before waiting on any repair keys.
    int probeFailedDeletes(String contentType, int limit) {
        jdbcTemplate.execute("SAVEPOINT backfill_delete_batch");
        List<Long> picked = jdbcTemplate.queryForList(PICK_FAILED_DELETES, Long.class, contentType, limit);
        if (picked.size() == limit) {
            LocalDateTime now = now();
            jdbcTemplate.update(REDRIVE_DELETES + " = ANY(?)", now, now, now,
                    new SqlArrayValue("bigint", picked.toArray()));
        } else {
            // A row updated since the statement snapshot can be locked and then
            // filtered out by PostgreSQL, even when the result is empty.
            jdbcTemplate.execute("ROLLBACK TO SAVEPOINT backfill_delete_batch");
        }
        jdbcTemplate.execute("RELEASE SAVEPOINT backfill_delete_batch");
        return picked.size();
    }

    int redriveUnlockedDeletes(String contentType, int limit) {
        LocalDateTime now = now();
        return jdbcTemplate.update("WITH picked AS (" + PICK_FAILED_DELETES + ") "
                        + REDRIVE_DELETES + " IN (SELECT job_id FROM picked)",
                contentType, limit, now, now, now);
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
