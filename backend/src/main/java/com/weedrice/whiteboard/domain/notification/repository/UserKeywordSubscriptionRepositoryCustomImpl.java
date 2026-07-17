package com.weedrice.whiteboard.domain.notification.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class UserKeywordSubscriptionRepositoryCustomImpl implements UserKeywordSubscriptionRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public int insertIgnore(Long userId, String keyword) {
        if (h2Database) {
            return insertIgnoreForH2(userId, keyword);
        }
        return jdbcTemplate.update("""
                INSERT INTO user_keyword_subscriptions
                    (user_id, keyword, last_notified_at, created_at, modified_at)
                VALUES (?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (user_id, keyword) DO NOTHING
                """, userId, keyword);
    }

    private int insertIgnoreForH2(Long userId, String keyword) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO user_keyword_subscriptions
                        (user_id, keyword, last_notified_at, created_at, modified_at)
                    VALUES (?, ?, NULL, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, userId, keyword);
        } catch (DuplicateKeyException ignored) {
            return 0;
        }
    }
}
