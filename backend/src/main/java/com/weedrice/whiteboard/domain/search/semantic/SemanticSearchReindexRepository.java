package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;
import java.util.List;

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
                    processing_started_at = NULL,
                    updated_at = CURRENT_TIMESTAMP
                """, scopeType, scopeId, phase);
    }

    List<Long> findPendingIds(int limit) {
        return jdbcTemplate.queryForList("""
                SELECT reindex_job_id FROM semantic_search_reindex_jobs
                WHERE status = 'PENDING' ORDER BY updated_at, reindex_job_id LIMIT ?
                """, Long.class, limit);
    }

    int claim(Long id, LocalDateTime now) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs SET status='PROCESSING', processing_started_at=?, updated_at=?
                WHERE reindex_job_id=? AND status='PENDING'
                """, now, now, id);
    }

    ReindexCursor findClaimed(Long id) {
        return jdbcTemplate.queryForObject("""
                SELECT reindex_job_id, scope_type, scope_id, content_phase, last_content_id
                FROM semantic_search_reindex_jobs WHERE reindex_job_id=? AND status='PROCESSING'
                """, (rs, row) -> new ReindexCursor(rs.getLong(1), rs.getString(2), rs.getLong(3),
                        rs.getString(4), rs.getLong(5)), id);
    }

    void advance(Long id, String phase, long cursor, boolean completed, LocalDateTime now) {
        jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs
                SET content_phase=?, last_content_id=?, status=?, processing_started_at=NULL, updated_at=?
                WHERE reindex_job_id=? AND status='PROCESSING'
                """, phase, cursor, completed ? "COMPLETED" : "PENDING", now, id);
    }

    int recoverStale(LocalDateTime staleBefore) {
        return jdbcTemplate.update("""
                UPDATE semantic_search_reindex_jobs
                SET status='PENDING', processing_started_at=NULL, updated_at=CURRENT_TIMESTAMP
                WHERE status='PROCESSING' AND (processing_started_at IS NULL OR processing_started_at < ?)
                """, staleBefore);
    }

    long countStatus(String status) {
        Long count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM semantic_search_reindex_jobs WHERE status=?", Long.class, status);
        return count == null ? 0 : count;
    }

    LocalDateTime findOldestActiveAt() {
        return jdbcTemplate.queryForObject("""
                SELECT MIN(CASE WHEN status='PROCESSING' THEN processing_started_at ELSE updated_at END)
                FROM semantic_search_reindex_jobs WHERE status IN ('PENDING', 'PROCESSING')
                """, LocalDateTime.class);
    }

    record ReindexCursor(Long id, String scopeType, Long scopeId, String phase, Long lastContentId) {}
}
