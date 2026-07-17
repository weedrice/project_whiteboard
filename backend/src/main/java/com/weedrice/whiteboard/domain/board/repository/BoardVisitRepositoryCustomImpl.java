package com.weedrice.whiteboard.domain.board.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class BoardVisitRepositoryCustomImpl implements BoardVisitRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public int upsert(Long userId, String boardUrl, LocalDateTime visitedAt) {
        if (h2Database) {
            return upsertForH2(userId, boardUrl, visitedAt);
        }
        return jdbcTemplate.update("""
                INSERT INTO board_visits (user_id, board_id, last_visited_at, created_at, modified_at)
                SELECT ?, board_id, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                FROM boards
                WHERE board_url = ?
                  AND EXISTS (SELECT 1 FROM users WHERE user_id = ?)
                ON CONFLICT (user_id, board_id) DO UPDATE
                SET last_visited_at = EXCLUDED.last_visited_at,
                    modified_at = CURRENT_TIMESTAMP
                """, userId, visitedAt, boardUrl, userId);
    }

    private int upsertForH2(Long userId, String boardUrl, LocalDateTime visitedAt) {
        int updated = jdbcTemplate.update("""
                UPDATE board_visits
                SET last_visited_at = ?, modified_at = CURRENT_TIMESTAMP
                WHERE user_id = ?
                  AND board_id = (SELECT board_id FROM boards WHERE board_url = ?)
                """, visitedAt, userId, boardUrl);
        if (updated > 0) {
            return updated;
        }
        try {
            return jdbcTemplate.update("""
                    INSERT INTO board_visits (user_id, board_id, last_visited_at, created_at, modified_at)
                    SELECT ?, board_id, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP
                    FROM boards
                    WHERE board_url = ?
                      AND EXISTS (SELECT 1 FROM users WHERE user_id = ?)
                    """, userId, visitedAt, boardUrl, userId);
        } catch (DuplicateKeyException ignored) {
            return jdbcTemplate.update("""
                    UPDATE board_visits
                    SET last_visited_at = ?, modified_at = CURRENT_TIMESTAMP
                    WHERE user_id = ?
                      AND board_id = (SELECT board_id FROM boards WHERE board_url = ?)
                    """, visitedAt, userId, boardUrl);
        }
    }
}
