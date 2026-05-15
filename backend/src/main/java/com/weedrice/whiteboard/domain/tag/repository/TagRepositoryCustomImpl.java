package com.weedrice.whiteboard.domain.tag.repository;

import lombok.RequiredArgsConstructor;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class TagRepositoryCustomImpl implements TagRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;

    @Override
    public int insertIgnore(String tagName) {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        if ("H2".equalsIgnoreCase(databaseProductName)) {
            return insertIgnoreForH2(tagName);
        }

        return jdbcTemplate.update("""
                INSERT INTO tags (tag_name, post_count, created_at, modified_at)
                VALUES (?, 0, CURRENT_TIMESTAMP, CURRENT_TIMESTAMP)
                ON CONFLICT (tag_name) DO NOTHING
                """, tagName);
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
