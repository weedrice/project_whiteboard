package com.weedrice.whiteboard.domain.tag.repository;

import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

import java.sql.Statement;
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
    public int insertIgnore(String tagName) {
        return insertIgnoreAll(List.of(tagName));
    }

    @Override
    public int insertIgnoreAll(Collection<String> tagNames) {
        if (tagNames == null || tagNames.isEmpty()) {
            return 0;
        }
        if (h2Database) {
            return tagNames.stream()
                    .mapToInt(this::insertIgnoreForH2)
                    .sum();
        }

        int[] insertedCounts = jdbcTemplate.batchUpdate("""
                INSERT INTO tags (tag_name, post_count, created_at, modified_at)
                VALUES (?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (tag_name) DO NOTHING
                """, tagNames.stream()
                .map(tagName -> new Object[] { tagName })
                .toList());
        return Arrays.stream(insertedCounts)
                .map(insertedCount -> insertedCount == Statement.SUCCESS_NO_INFO ? 0 : Math.max(insertedCount, 0))
                .sum();
    }

    private int insertIgnoreForH2(String tagName) {
        try {
            return jdbcTemplate.update("""
                    INSERT INTO tags (tag_name, post_count, created_at, modified_at)
                    VALUES (?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                    """, tagName);
        } catch (DuplicateKeyException ignored) {
            return 0;
        }
    }
}
