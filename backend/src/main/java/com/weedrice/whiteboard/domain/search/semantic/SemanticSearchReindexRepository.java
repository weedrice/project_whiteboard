package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

@Repository
@RequiredArgsConstructor
class SemanticSearchReindexRepository {
    private static final String CONTENT_QUEUE = "semantic_search_reindex_jobs";
    private static final String AUTHOR_QUEUE = "semantic_search_author_reindex_jobs";
    private static final String CURSOR_COLUMNS = """
            reindex_job_id, scope_type, scope_id, content_phase, last_content_id, retry_count,
            status, lease_token, processing_started_at, next_attempt_at, updated_at
            """;
    // Both queues draw IDs from the same sequence. Enumerate the fields since
    // V71 added columns to the original table in a different physical order.
    private static final String ALL_JOBS = "(SELECT " + CURSOR_COLUMNS + " FROM " + CONTENT_QUEUE
            + " UNION ALL SELECT " + CURSOR_COLUMNS + " FROM " + AUTHOR_QUEUE + ") jobs";

    private final JdbcTemplate jdbcTemplate;

    void enqueue(String scopeType, Long scopeId) {
        String phase = "POST_COMMENTS".equals(scopeType) ? "COMMENT" : "POST";
        String queue = "AUTHOR".equals(scopeType) ? AUTHOR_QUEUE : CONTENT_QUEUE;
        jdbcTemplate.update("""
                INSERT INTO %s
                    (scope_type, scope_id, content_phase, last_content_id, status, created_at, updated_at)
                VALUES (?, ?, ?, 0, 'PENDING', CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (scope_type, scope_id) DO UPDATE SET
                    content_phase = EXCLUDED.content_phase,
                    last_content_id = 0,
                    status = 'PENDING',
                    retry_count = 0,
                    next_attempt_at = CURRENT_TIMESTAMP,
                    last_error = NULL,
                    lease_token = NULL,
                    failed_at = NULL,
                    processing_started_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                """.formatted(queue), scopeType, scopeId, phase);
    }

    List<Long> findRunnableIds(LocalDateTime now, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT reindex_job_id FROM %s
                WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY next_attempt_at, updated_at, reindex_job_id LIMIT ?
                """.formatted(ALL_JOBS), Long.class, now, limit);
    }

    int claim(Long id, UUID leaseToken, LocalDateTime now) {
        return updateById("""
                UPDATE %s
                SET status='PROCESSING', processing_started_at=?, lease_token=?, updated_at=?
                WHERE reindex_job_id=? AND status='PENDING' AND next_attempt_at <= ?
                """, now, leaseToken, now, id, now);
    }

    ReindexCursor findClaimed(Long id, UUID leaseToken) {
        return jdbcTemplate.queryForObject("""
                SELECT reindex_job_id, scope_type, scope_id, content_phase, last_content_id, retry_count
                FROM %s
                WHERE reindex_job_id=? AND status='PROCESSING' AND lease_token=?
                """.formatted(ALL_JOBS), (rs, row) -> new ReindexCursor(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getString(4), rs.getLong(5), rs.getInt(6), leaseToken), id, leaseToken);
    }

    int advance(Long id, UUID leaseToken, String phase, long cursor, boolean completed, LocalDateTime now) {
        return updateById("""
                UPDATE %s
                SET content_phase=?, last_content_id=?, status=?, retry_count=0,
                    next_attempt_at=?, last_error=NULL, lease_token=NULL, failed_at=NULL,
                    processing_started_at=NULL, updated_at=?
                WHERE reindex_job_id=? AND status='PROCESSING' AND lease_token=?
                """, phase, cursor, completed ? "COMPLETED" : "PENDING", now, now, id, leaseToken);
    }

    int markFailed(Long id, UUID leaseToken, int maxRetryCount, LocalDateTime nextAttemptAt,
                   LocalDateTime now, String error) {
        return updateById("""
                UPDATE %s
                SET retry_count=LEAST(retry_count + 1, ?),
                    status=CASE WHEN retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at=CASE WHEN retry_count + 1 >= ? THEN ? ELSE ? END,
                    last_error=?,
                    failed_at=CASE WHEN retry_count + 1 >= ? THEN ? ELSE NULL END,
                    processing_started_at=NULL,
                    lease_token=NULL,
                    updated_at=?
                WHERE reindex_job_id=? AND status='PROCESSING' AND lease_token=?
                """, maxRetryCount, maxRetryCount, maxRetryCount, now, nextAttemptAt, error,
                maxRetryCount, now, now, id, leaseToken);
    }

    int recoverStale(LocalDateTime staleBefore, int maxRetryCount, LocalDateTime nextAttemptAt,
                     LocalDateTime now, String error) {
        String sql = """
                UPDATE %s
                SET retry_count=LEAST(retry_count + 1, ?),
                    status=CASE WHEN retry_count + 1 >= ? THEN 'FAILED' ELSE 'PENDING' END,
                    next_attempt_at=CASE WHEN retry_count + 1 >= ? THEN ? ELSE ? END,
                    last_error=?,
                    failed_at=CASE WHEN retry_count + 1 >= ? THEN ? ELSE NULL END,
                    processing_started_at=NULL,
                    lease_token=NULL,
                    updated_at=?
                WHERE status='PROCESSING'
                  AND (processing_started_at IS NULL OR processing_started_at < ?)
                """;
        Object[] args = {maxRetryCount, maxRetryCount, maxRetryCount, now, nextAttemptAt, error,
                maxRetryCount, now, now, staleBefore};
        return jdbcTemplate.update(sql.formatted(CONTENT_QUEUE), args)
                + jdbcTemplate.update(sql.formatted(AUTHOR_QUEUE), args);
    }

    int redrive(Long id, LocalDateTime now) {
        return updateById("""
                UPDATE %s
                SET status='PENDING', retry_count=0, next_attempt_at=?, last_error=NULL,
                    failed_at=NULL, processing_started_at=NULL, lease_token=NULL, updated_at=?
                WHERE reindex_job_id=? AND status='FAILED'
                """, now, now, id);
    }

    long countStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM " + ALL_JOBS + " WHERE status=?", Long.class, status);
        return count == null ? 0 : count;
    }

    LocalDateTime findOldestActiveAt() {
        return jdbcTemplate.queryForObject("""
                SELECT MIN(CASE WHEN status='PROCESSING' THEN processing_started_at ELSE next_attempt_at END)
                FROM %s WHERE status IN ('PENDING', 'PROCESSING')
                """.formatted(ALL_JOBS), LocalDateTime.class);
    }

    private int updateById(String sql, Object... args) {
        int changed = jdbcTemplate.update(sql.formatted(CONTENT_QUEUE), args);
        return changed == 0 ? jdbcTemplate.update(sql.formatted(AUTHOR_QUEUE), args) : changed;
    }

    record ReindexCursor(Long id, String scopeType, Long scopeId, String phase, Long lastContentId,
                         int retryCount, UUID leaseToken) {}
}
