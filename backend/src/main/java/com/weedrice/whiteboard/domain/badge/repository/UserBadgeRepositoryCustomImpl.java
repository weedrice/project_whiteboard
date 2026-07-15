package com.weedrice.whiteboard.domain.badge.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.time.LocalDateTime;

@Repository
@RequiredArgsConstructor
public class UserBadgeRepositoryCustomImpl implements UserBadgeRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public int insertIgnore(Long userId, String badgeCode, LocalDateTime acquiredAt) {
        if (h2Database) {
            return insertIgnoreForH2(userId, badgeCode, acquiredAt);
        }
        return jdbcTemplate.update("""
                INSERT INTO user_badges (
                    user_id,
                    badge_code,
                    acquired_at,
                    created_at,
                    modified_at
                )
                VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT ON CONSTRAINT uk_user_badges_user_badge DO NOTHING
                """, userId, badgeCode, acquiredAt);
    }

    private int insertIgnoreForH2(Long userId, String badgeCode, LocalDateTime acquiredAt) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO user_badges (
                        user_id,
                        badge_code,
                        acquired_at,
                        created_at,
                        modified_at
                    )
                    VALUES (?, ?, ?, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, userId, badgeCode, acquiredAt);
        } catch (DuplicateKeyException ignored) {
            return 0;
        }
    }
}
