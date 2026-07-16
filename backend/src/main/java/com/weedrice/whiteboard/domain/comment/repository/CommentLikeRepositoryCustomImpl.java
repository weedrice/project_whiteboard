package com.weedrice.whiteboard.domain.comment.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class CommentLikeRepositoryCustomImpl implements CommentLikeRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public int insertIgnore(Long userId, Long commentId) {
        if (h2Database) {
            return insertIgnoreForH2(userId, commentId);
        }
        return jdbcTemplate.update("""
                INSERT INTO comment_likes (user_id, comment_id, created_at, modified_at)
                VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (comment_id, user_id) DO NOTHING
                """, userId, commentId);
    }

    private int insertIgnoreForH2(Long userId, Long commentId) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO comment_likes (user_id, comment_id, created_at, modified_at)
                    VALUES (?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, userId, commentId);
        } catch (DuplicateKeyException ignored) {
            return 0;
        }
    }
}
