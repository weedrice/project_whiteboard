package com.weedrice.whiteboard.domain.feed.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class FeedGenerationJobRepositoryCustomImpl implements FeedGenerationJobRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public int insertIgnore(Long postId, Long boardId) {
        if (h2Database) {
            return insertIgnoreForH2(postId, boardId);
        }
        return jdbcTemplate.update("""
                INSERT INTO feed_generation_jobs (
                    post_id, board_id, status, retry_count, next_attempt_at, created_at, modified_at
                )
                VALUES (?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (post_id) DO NOTHING
                """, postId, boardId);
    }

    private int insertIgnoreForH2(Long postId, Long boardId) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO feed_generation_jobs (
                        post_id, board_id, status, retry_count, next_attempt_at, created_at, modified_at
                    )
                    VALUES (?, ?, 'PENDING', 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, postId, boardId);
        } catch (DuplicateKeyException ignored) {
            return 0;
        }
    }
}
