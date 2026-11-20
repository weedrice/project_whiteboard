package com.weedrice.whiteboard.domain.tag.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;

@Repository
@RequiredArgsConstructor
public class TagRepositoryCustomImpl implements TagRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public void lockOrCreateForAssignment(Collection<String> tagNames) {
        List<String> orderedNames = tagNames.stream().distinct().sorted().toList();
        if (orderedNames.isEmpty()) {
            return;
        }
        if (h2Database) {
            for (String name : orderedNames) {
                if (jdbcTemplate.queryForList("SELECT tag_id FROM tags WHERE tag_name = ? FOR UPDATE",
                        Long.class, name).isEmpty()) {
                    createMissingTagForH2(name);
                }
            }
            return;
        }
        // Keep existing and concurrently created tags in the same lock order.
        // A false conflict predicate locks the winning insert without rewriting its row.
        jdbcTemplate.batchUpdate("""
                WITH locked AS (
                    SELECT tag_id FROM tags WHERE tag_name = ? FOR UPDATE
                )
                INSERT INTO tags (tag_name, post_count, created_at, modified_at)
                SELECT ?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                WHERE NOT EXISTS (SELECT 1 FROM locked)
                ON CONFLICT (tag_name) DO UPDATE SET tag_name = EXCLUDED.tag_name WHERE FALSE
                """, orderedNames.stream().map(name -> new Object[]{name, name}).toList());
    }

    @Override
    public int deleteOrphanBatch(LocalDateTime cutoff, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (h2Database) {
            return deleteOrphanBatchForH2(cutoff, batchSize);
        }
        return jdbcTemplate.update("""
                WITH candidates AS (
                    SELECT t.tag_id
                    FROM tags t
                    WHERE t.post_count = 0
                      AND t.created_at < ?
                      AND NOT EXISTS (
                          SELECT 1 FROM post_tags pt WHERE pt.tag_id = t.tag_id
                      )
                    ORDER BY t.created_at ASC, t.tag_id ASC
                    FOR UPDATE SKIP LOCKED
                    LIMIT ?
                )
                DELETE FROM tags t
                USING candidates c
                WHERE t.tag_id = c.tag_id
                  AND t.post_count = 0
                  AND NOT EXISTS (
                      SELECT 1 FROM post_tags pt WHERE pt.tag_id = t.tag_id
                  )
                """, cutoff, batchSize);
    }

    private int deleteOrphanBatchForH2(LocalDateTime cutoff, int batchSize) {
        List<Long> ids = jdbcTemplate.queryForList("""
                SELECT t.tag_id
                FROM tags t
                WHERE t.post_count = 0
                  AND t.created_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM post_tags pt WHERE pt.tag_id = t.tag_id
                  )
                ORDER BY t.created_at ASC, t.tag_id ASC
                LIMIT ?
                """, Long.class, cutoff, batchSize);
        if (ids.isEmpty()) {
            return 0;
        }
        int[] deletedCounts = jdbcTemplate.batchUpdate("""
                DELETE FROM tags
                WHERE tag_id = ?
                  AND post_count = 0
                  AND created_at < ?
                  AND NOT EXISTS (
                      SELECT 1 FROM post_tags pt WHERE pt.tag_id = tags.tag_id
                  )
                """, ids.stream().map(id -> new Object[] { id, cutoff }).toList());
        return Arrays.stream(deletedCounts)
                .map(count -> count == Statement.SUCCESS_NO_INFO ? 1 : Math.max(count, 0))
                .sum();
    }

    private void createMissingTagForH2(String tagName) {
        try {
            jdbcTemplate.update("""
                    INSERT INTO tags (tag_name, post_count, created_at, modified_at)
                    VALUES (?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, tagName);
        } catch (DuplicateKeyException ignored) {
            jdbcTemplate.queryForList("SELECT tag_id FROM tags WHERE tag_name = ? FOR UPDATE", Long.class, tagName);
        }
    }
}
