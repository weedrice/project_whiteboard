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
    private final JdbcTemplate jdbcTemplate;

    void enqueue(String scopeType, Long scopeId) {
        String phase = "BOARD".equals(scopeType) ? "POST" : "COMMENT";
        jdbcTemplate.update("""
                INSERT INTO semantic_search_reindex_jobs
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
                """, scopeType, scopeId, phase);
    }

    List<Long> findRunnableIds(LocalDateTime now, int limit) {
        return jdbcTemplate.queryForList("""
                SELECT reindex_job_id FROM semantic_search_reindex_jobs
                WHERE status = 'PENDING' AND next_attempt_at <= ?
                ORDER BY next_attempt_at, updated_at, reindex_job_id LIMIT ?
                """, Long.class, now, limit);
    }

    int claim(Long id, UUID leaseToken, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs
                SET status='PROCESSING', processing_started_at=?, lease_token=?, updated_at=?
                WHERE reindex_job_id=? AND status='PENDING' AND next_attempt_at <= ?
                """, now, leaseToken, now, id, now);
    }

    ReindexCursor findClaimed(Long id, UUID leaseToken) {
        return jdbcTemplate.queryForObject("""
                SELECT reindex_job_id, scope_type, scope_id, content_phase, last_content_id, retry_count
                FROM semantic_search_reindex_jobs
                WHERE reindex_job_id=? AND status='PROCESSING' AND lease_token=?
                """, (rs, row) -> new ReindexCursor(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getString(4), rs.getLong(5), rs.getInt(6), leaseToken), id, leaseToken);
    }

    int advance(Long id, UUID leaseToken, String phase, long cursor, boolean completed, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs
                SET content_phase=?, last_content_id=?, status=?, retry_count=0,
                    next_attempt_at=?, last_error=NULL, lease_token=NULL, failed_at=NULL,
                    processing_started_at=NULL, updated_at=?
                WHERE reindex_job_id=? AND status='PROCESSING' AND lease_token=?
                """, phase, cursor, completed ? "COMPLETED" : "PENDING", now, now, id, leaseToken);
    }

    int markFailed(Long id, UUID leaseToken, int maxRetryCount, LocalDateTime nextAttemptAt,
                   LocalDateTime now, String error) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs
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
        return jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs
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
                """, maxRetryCount, maxRetryCount, maxRetryCount, now, nextAttemptAt, error,
                maxRetryCount, now, now, staleBefore);
    }

    int redrive(Long id, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs
                SET status='PENDING', retry_count=0, next_attempt_at=?, last_error=NULL,
                    failed_at=NULL, processing_started_at=NULL, lease_token=NULL, updated_at=?
                WHERE reindex_job_id=? AND status='FAILED'
                """, now, now, id);
    }

    long countStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM semantic_search_reindex_jobs WHERE status=?", Long.class, status);
        return count == null ? 0 : count;
    }

    LocalDateTime findOldestActiveAt() {
        return jdbcTemplate.queryForObject("""
                SELECT MIN(CASE WHEN status='PROCESSING' THEN processing_started_at ELSE next_attempt_at END)
                FROM semantic_search_reindex_jobs WHERE status IN ('PENDING', 'PROCESSING')
                """, LocalDateTime.class);
    }

    record ReindexCursor(Long id, String scopeType, Long scopeId, String phase, Long lastContentId,
                         int retryCount, UUID leaseToken) {}
}
