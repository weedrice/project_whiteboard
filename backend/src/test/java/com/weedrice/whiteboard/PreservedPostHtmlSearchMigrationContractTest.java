package com.weedrice.whiteboard;

import org.junit.jupiter.api.Test;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import static org.assertj.core.api.Assertions.assertThat;

class PreservedPostHtmlSearchMigrationContractTest {

    @Test
    void v88AddsPreservedHtmlExpansionAndIndexedSearchContract() throws IOException {
        String sql;
        try (var input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V88__search_preserved_post_html.sql")) {
            assertThat(input).isNotNull();
            sql = new String(input.readAllBytes(), StandardCharsets.UTF_8);
        }

        assertThat(sql)
                .contains("-- noviis:migration-phase expand")
                .contains("CREATE OR REPLACE FUNCTION noviis_expand_preserved_post_html")
                .contains("decode(marker_match[2], 'base64')")
                .contains("DROP INDEX CONCURRENTLY IF EXISTS idx_posts_contents_trgm")
                .contains("CREATE INDEX CONCURRENTLY IF NOT EXISTS idx_posts_expanded_contents_trgm")
                .contains("idx_posts_expanded_contents_trgm")
                .contains("lower(noviis_expand_preserved_post_html(contents)) gin_trgm_ops");

        try (var input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V88__search_preserved_post_html.sql.conf")) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8).trim())
                    .isEqualTo("executeInTransaction=false");
        }
    }
}
