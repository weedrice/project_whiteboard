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
                .contains("-- noviis:migration-phase contract")
                .contains("-- noviis:design-doc docs/design-notes/preserved-post-html-search-index-2026-08-10.md")
                .contains("CREATE OR REPLACE FUNCTION noviis_expand_preserved_post_html")
                .contains("decode(marker_match[2], 'base64')")
                .contains("CREATE INDEX CONCURRENTLY idx_posts_expanded_contents_trgm")
                .contains("idx_posts_expanded_contents_trgm")
                .contains("lower(noviis_expand_preserved_post_html(contents)) gin_trgm_ops")
                .doesNotContain("CREATE INDEX CONCURRENTLY IF NOT EXISTS")
                .doesNotContain("DROP INDEX");

        try (var input = getClass().getClassLoader().getResourceAsStream(
                "db/migration/V88__search_preserved_post_html.sql.conf")) {
            assertThat(input).isNotNull();
            assertThat(new String(input.readAllBytes(), StandardCharsets.UTF_8).trim())
                    .isEqualTo("executeInTransaction=false");
        }
    }
}
