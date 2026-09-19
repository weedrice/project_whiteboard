package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.Test;
import org.springframework.jdbc.core.namedparam.MapSqlParameterSource;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;

import java.util.List;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

class SemanticSearchSqlBuilderTest {

    private final SemanticSearchVectorRepository vectorRepository =
            new SemanticSearchVectorRepository(mock(NamedParameterJdbcTemplate.class));
    private final SemanticSearchKeywordFallbackRepository keywordRepository =
            new SemanticSearchKeywordFallbackRepository(mock(NamedParameterJdbcTemplate.class));

    @Test
    void vectorSql_coversContentTypeMatrix() {
        for (SemanticSearchContentType contentType : SemanticSearchContentType.values()) {
            SemanticSearchQuery query = vectorQuery(contentType, null, List.of());

            String searchSql = vectorRepository.searchSql(query);
            String countSql = vectorRepository.countSql(query);

            assertContentTypeSelection(searchSql, contentType);
            assertVectorCountSelection(countSql, contentType);
            assertThat(searchSql)
                    .contains("CAST(:queryEmbedding AS vector)")
                    .contains("ORDER BY similarity DESC, created_at DESC, content_id DESC")
                    .contains("LIMIT :limit OFFSET :offset");
            assertThat(countSql)
                    .contains("SELECT COUNT(*) FROM")
                    .doesNotContain("ORDER BY")
                    .doesNotContain("LIMIT :limit")
                    .doesNotContain("OFFSET :offset")
                    .doesNotContain("CAST(:queryEmbedding AS vector)")
                    .doesNotContain(" AS similarity")
                    .doesNotContain("author_display_name");
        }
    }

    @Test
    void keywordSql_coversContentTypeMatrix() {
        for (SemanticSearchContentType contentType : SemanticSearchContentType.values()) {
            SemanticSearchKeywordQuery query = keywordQuery(contentType, null, List.of());

            String searchSql = keywordRepository.searchSql(query);
            String countSql = keywordRepository.countSql(query);

            assertContentTypeSelection(searchSql, contentType);
            assertKeywordCountSelection(countSql, contentType);
            assertThat(searchSql)
                    .contains("LIKE :keywordPattern ESCAPE '!'")
                    .contains("p.is_blinded = 'N'")
                    .contains("ORDER BY created_at DESC, content_type ASC, content_id DESC")
                    .contains("LIMIT :limit OFFSET :offset");
            assertThat(countSql)
                    .contains("SELECT COUNT(*) FROM")
                    .contains("p.is_blinded = 'N'")
                    .contains("LIKE :keywordPattern ESCAPE '!'")
                    .doesNotContain("ORDER BY")
                    .doesNotContain("LIMIT :limit")
                    .doesNotContain("OFFSET :offset")
                    .doesNotContain("author_display_name");
        }
    }

    @Test
    void sqlPredicates_coverBoardAccessAndBlockedUsers() {
        SemanticSearchQuery boardScoped = vectorQuery(SemanticSearchContentType.ALL, "private", List.of(9L));
        SemanticSearchKeywordQuery publicFallback = keywordQuery(SemanticSearchContentType.ALL, null, List.of());

        assertThat(vectorRepository.searchSql(boardScoped))
                .contains("b.is_active = 'Y' AND b.is_public = 'Y'")
                .contains("b.board_url = :boardUrl")
                .contains("p.is_secret = 'N'")
                .contains("p.user_id NOT IN (:blockedUserIds)")
                .contains("u.user_id NOT IN (:blockedUserIds)")
                .doesNotContain("b.is_listed = 'Y'")
                .doesNotContain(":viewerSuperAdmin = TRUE")
                .doesNotContain("adm.is_active = 'Y'");
        assertThat(vectorRepository.countSql(boardScoped))
                .contains("b.is_active = 'Y' AND b.is_public = 'Y'")
                .contains("b.board_url = :boardUrl")
                .contains("p.is_secret = 'N'")
                .contains("p.user_id NOT IN (:blockedUserIds)")
                .contains("u.user_id NOT IN (:blockedUserIds)")
                .doesNotContain("b.is_listed = 'Y'")
                .doesNotContain(":viewerSuperAdmin = TRUE")
                .doesNotContain("adm.is_active = 'Y'");

        assertThat(keywordRepository.searchSql(publicFallback))
                .contains("b.is_active = 'Y' AND b.is_public = 'Y'")
                .contains("(b.is_listed = 'Y' OR b.is_listed IS NULL)")
                .contains("p.is_secret = 'N'")
                .doesNotContain("NOT IN (:blockedUserIds)");
        assertThat(keywordRepository.countSql(publicFallback))
                .contains("b.is_active = 'Y' AND b.is_public = 'Y'")
                .contains("(b.is_listed = 'Y' OR b.is_listed IS NULL)")
                .contains("p.is_secret = 'N'")
                .doesNotContain("NOT IN (:blockedUserIds)");
    }

    @Test
    void params_useBlockedSentinelAndEscapeKeywordWildcards() {
        MapSqlParameterSource vectorParams =
                vectorRepository.params(vectorQuery(SemanticSearchContentType.POST, null, List.of()));
        MapSqlParameterSource keywordParams =
                keywordRepository.params(new SemanticSearchKeywordQuery(
                        SemanticSearchContentType.COMMENT,
                        "100%_match!",
                        "free",
                        List.of(9L),
                        20,
                        40));

        assertThat(vectorParams.getValue("blockedUserIds")).isEqualTo(List.of(-1L));
        assertThat(vectorParams.getValue("queryEmbedding")).isEqualTo("[0.1,0.2]");
        assertThat(keywordParams.getValue("blockedUserIds")).isEqualTo(List.of(9L));
        assertThat(keywordParams.getValue("keywordPattern")).isEqualTo("%100!%!_match!!%");
        assertThat(keywordParams.getValue("boardUrl")).isEqualTo("free");
    }

    @Test
    void authorVisibility_appliesToSearchAndCountForEveryContentType() {
        for (SemanticSearchContentType contentType : SemanticSearchContentType.values()) {
            SemanticSearchQuery vector = vectorQuery(contentType, null, List.of());
            SemanticSearchKeywordQuery keyword = keywordQuery(contentType, null, List.of());
            for (String sql : List.of(vectorRepository.searchSql(vector), vectorRepository.countSql(vector),
                    keywordRepository.searchSql(keyword), keywordRepository.countSql(keyword))) {
                String normalized = sql.replaceAll("\\s+", " ");
                int contentAuthors = (contentType.includesPosts() ? 1 : 0) + (contentType.includesComments() ? 1 : 0);
                assertAuthorVisibility(normalized, "u", contentAuthors);
                assertAuthorVisibility(normalized, "post_author", contentType.includesComments() ? 1 : 0);
            }
        }
    }

    private static void assertAuthorVisibility(String sql, String alias, int expectedOccurrences) {
        String banExclusion = "AND NOT EXISTS ( SELECT 1 FROM sanctions s WHERE s.target_user_id = "
                + alias + ".user_id AND UPPER(s.type) = 'BAN' AND s.start_date <= CURRENT_TIMESTAMP "
                + "AND (s.end_date IS NULL OR s.end_date > CURRENT_TIMESTAMP) )";
        for (String condition : List.of("AND " + alias + ".status = 'ACTIVE'",
                "AND " + alias + ".deleted_at IS NULL", banExclusion)) {
            assertThat(Pattern.compile(Pattern.quote(condition)).matcher(sql).results().count())
                    .as("%s occurs in each applicable content branch", condition)
                    .isEqualTo(expectedOccurrences);
        }
    }

    private static void assertContentTypeSelection(String sql, SemanticSearchContentType contentType) {
        if (contentType.includesPosts()) {
            assertThat(sql).contains("'POST' AS content_type");
        } else {
            assertThat(sql).doesNotContain("'POST' AS content_type");
        }
        if (contentType.includesComments()) {
            assertThat(sql).contains("'COMMENT' AS content_type");
        } else {
            assertThat(sql).doesNotContain("'COMMENT' AS content_type");
        }
        if (contentType == SemanticSearchContentType.ALL) {
            assertThat(sql).contains("UNION ALL");
        } else {
            assertThat(sql).doesNotContain("UNION ALL");
        }
    }

    private static void assertVectorCountSelection(String sql, SemanticSearchContentType contentType) {
        assertCountSelection(
                sql,
                contentType,
                "WHERE e.content_type = 'POST'",
                "WHERE e.content_type = 'COMMENT'");
    }

    private static void assertKeywordCountSelection(String sql, SemanticSearchContentType contentType) {
        assertCountSelection(
                sql,
                contentType,
                "SELECT p.post_id AS content_id",
                "SELECT c.comment_id AS content_id");
    }

    private static void assertCountSelection(
            String sql,
            SemanticSearchContentType contentType,
            String postMarker,
            String commentMarker) {
        if (contentType.includesPosts()) {
            assertThat(sql).contains(postMarker);
        } else {
            assertThat(sql).doesNotContain(postMarker);
        }
        if (contentType.includesComments()) {
            assertThat(sql).contains(commentMarker);
        } else {
            assertThat(sql).doesNotContain(commentMarker);
        }
        if (contentType == SemanticSearchContentType.ALL) {
            assertThat(sql).contains("UNION ALL");
        } else {
            assertThat(sql).doesNotContain("UNION ALL");
        }
    }

    private static SemanticSearchQuery vectorQuery(
            SemanticSearchContentType contentType,
            String boardUrl,
            List<Long> blockedUserIds) {
        return new SemanticSearchQuery(
                contentType,
                boardUrl,
                blockedUserIds,
                "[0.1,0.2]",
                20,
                40);
    }

    private static SemanticSearchKeywordQuery keywordQuery(
            SemanticSearchContentType contentType,
            String boardUrl,
            List<Long> blockedUserIds) {
        return new SemanticSearchKeywordQuery(
                contentType,
                "hello",
                boardUrl,
                blockedUserIds,
                20,
                40);
    }
}
