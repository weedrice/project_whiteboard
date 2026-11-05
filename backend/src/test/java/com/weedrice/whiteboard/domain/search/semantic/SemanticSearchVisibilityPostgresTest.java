package com.weedrice.whiteboard.domain.search.semantic;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * Executes the production SQL against pgvector without loading application services or making API calls.
 * Each test creates a private schema inside a transaction and rolls back all fixtures, including DDL.
 */
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
class SemanticSearchVisibilityPostgresTest {

    private static final String VECTOR = "[1" + ",0".repeat(1535) + "]";
    private static final List<Long> BLOCKED_USERS = List.of(6L);
    private static final List<Long> VISIBLE_AUTHORS = List.of(1L, 7L, 8L, 9L, 10L, 12L);

    private Connection connection;
    private JdbcTemplate jdbc;
    private SemanticSearchVectorRepository vectors;
    private SemanticSearchKeywordFallbackRepository keywords;

    @BeforeEach
    void createIsolatedFixtures() throws SQLException {
        connection = DriverManager.getConnection(
                environment("POSTGRES_SMOKE_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/noviis_ci_app"),
                environment("POSTGRES_SMOKE_DATASOURCE_USERNAME", "noviis_ci"),
                environment("POSTGRES_SMOKE_DATASOURCE_PASSWORD", "noviis_ci"));
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        jdbc.execute("CREATE EXTENSION IF NOT EXISTS vector");
        String schema = "semantic_visibility_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        jdbc.execute("SET LOCAL search_path TO " + schema + ", public");
        createTables();
        NamedParameterJdbcTemplate namedJdbc = new NamedParameterJdbcTemplate(jdbc);
        vectors = new SemanticSearchVectorRepository(namedJdbc);
        keywords = new SemanticSearchKeywordFallbackRepository(namedJdbc);
        seedAuthorsAndContent();
    }

    @AfterEach
    void rollbackFixtures() throws SQLException {
        if (connection != null) {
            try {
                connection.rollback();
            } finally {
                connection.close();
            }
        }
    }

    @Test
    void relatedPosts_excludeHiddenAuthorsBeforeLimitAndRespectBanTimeBoundaries() {
        SemanticSearchQueryContext context = new SemanticSearchQueryContext("free", BLOCKED_USERS);

        SemanticRelatedPostResult firstPage = vectors.findRelatedPostIds(1L, context, 3, 0.55);
        SemanticRelatedPostResult all = vectors.findRelatedPostIds(1L, context, 20, 0.55);

        assertThat(firstPage.sourceEmbeddingAvailable()).isTrue();
        assertThat(firstPage.postIds()).containsExactly(12L, 10L, 9L);
        // MUTE, expired BAN, future BAN, and end == now remain visible. Start == now is hidden.
        assertThat(all.postIds()).containsExactly(12L, 10L, 9L, 8L, 7L);
        assertThat(all.postIds()).doesNotHaveDuplicates();
    }

    @ParameterizedTest
    @EnumSource(SemanticSearchContentType.class)
    void vectorAndKeywordResults_shareAuthorEligibilityWithCountsAndPagination(
            SemanticSearchContentType contentType) {
        SemanticSearchQuery vectorQuery = vectorQuery(contentType, 100, 0);
        SemanticSearchKeywordQuery keywordQuery = keywordQuery(contentType, 100, 0);
        List<SemanticSearchRow> vectorRows = vectors.search(vectorQuery);
        List<SemanticSearchRow> keywordRows = keywords.search(keywordQuery);
        List<String> expected = expectedContent(contentType);

        assertThat(keys(vectorRows)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(keys(keywordRows)).containsExactlyInAnyOrderElementsOf(expected);
        assertThat(vectors.count(vectorQuery)).isEqualTo(expected.size());
        assertThat(keywords.count(keywordQuery)).isEqualTo(expected.size());
        assertThat(keys(vectorRows)).doesNotHaveDuplicates();
        assertThat(keys(keywordRows)).doesNotHaveDuplicates();

        SemanticSearchQuery vectorPage = vectorQuery(contentType, 2, 1);
        SemanticSearchKeywordQuery keywordPage = keywordQuery(contentType, 2, 1);
        assertThat(keys(vectors.search(vectorPage))).containsExactlyElementsOf(keys(vectorRows).subList(1, 3));
        assertThat(keys(keywords.search(keywordPage))).containsExactlyElementsOf(keys(keywordRows).subList(1, 3));
        assertThat(vectors.count(vectorPage)).isEqualTo(expected.size());
        assertThat(keywords.count(keywordPage)).isEqualTo(expected.size());
    }

    @Test
    void sanctionChanges_takeEffectWithoutRewritingStoredEmbeddings() {
        SemanticSearchQueryContext context = new SemanticSearchQueryContext("free", BLOCKED_USERS);
        assertThat(vectors.findRelatedPostIds(1L, context, 20, 0.55).postIds()).contains(10L);
        Long embeddingCount = jdbc.queryForObject("SELECT COUNT(*) FROM semantic_search_embeddings", Long.class);

        jdbc.update("""
                INSERT INTO sanctions (target_user_id, type, start_date, end_date)
                VALUES (10, 'BAN', CURRENT_TIMESTAMP, NULL)
                """);
        assertThat(vectors.findRelatedPostIds(1L, context, 20, 0.55).postIds()).doesNotContain(10L);
        assertThat(keys(vectors.search(vectorQuery(SemanticSearchContentType.COMMENT, 100, 0))))
                .doesNotContain("COMMENT:110", "COMMENT:203");

        jdbc.update("UPDATE sanctions SET end_date = CURRENT_TIMESTAMP WHERE target_user_id = 10");
        assertThat(vectors.findRelatedPostIds(1L, context, 20, 0.55).postIds()).contains(10L);
        assertThat(keys(vectors.search(vectorQuery(SemanticSearchContentType.COMMENT, 100, 0))))
                .contains("COMMENT:110", "COMMENT:203");
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM semantic_search_embeddings", Long.class))
                .isEqualTo(embeddingCount);
    }

    @Test
    void hybridIncludesUnindexedExactTitleAndLowSimilarityKeywordWithoutHiddenAuthors() {
        jdbc.update("""
                INSERT INTO posts (post_id, board_id, user_id, title, contents, created_at)
                VALUES (500, 1, 1, 'opposite vector', 'needle body', CURRENT_TIMESTAMP),
                       (501, 1, 1, 'needle', 'not yet indexed', CURRENT_TIMESTAMP),
                       (502, 1, 6, 'needle', 'blocked author', CURRENT_TIMESTAMP)
                """);
        String opposite = "[-1" + ",0".repeat(1535) + "]";
        jdbc.update("INSERT INTO semantic_search_embeddings (content_type, content_id, embedding) VALUES ('POST', 500, CAST(? AS vector))", opposite);
        var vectorQuery = new SemanticSearchQuery(SemanticSearchContentType.POST, "free", BLOCKED_USERS, VECTOR, 2, 0, 0.8, 200);
        var keywordQuery = new SemanticSearchKeywordQuery(SemanticSearchContentType.POST, "needle", "free", BLOCKED_USERS, 2, 0, 200);
        assertThat(keys(vectors.search(vectorQuery.candidates()))).doesNotContain("POST:500", "POST:501", "POST:502");
        assertThat(vectors.count(vectorQuery)).isEqualTo(VISIBLE_AUTHORS.size());
        var service = new SemanticSearchQueryTransactionService(vectors, keywords,
                org.mockito.Mockito.mock(com.weedrice.whiteboard.domain.board.repository.BoardRepository.class),
                org.mockito.Mockito.mock(com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy.class),
                org.mockito.Mockito.mock(com.weedrice.whiteboard.domain.user.repository.UserRepository.class),
                org.mockito.Mockito.mock(com.weedrice.whiteboard.domain.user.service.UserBlockService.class));
        var first = service.searchHybrid(vectorQuery, keywordQuery);
        assertThat(first.results().getFirst().contentId()).isEqualTo(501L);
        assertThat(first.results().getFirst().rankSource()).isEqualTo("KEYWORD_FALLBACK");
        assertThat(first.totalElements()).isEqualTo(VISIBLE_AUTHORS.size() + 2L);
        List<String> allPages = new ArrayList<>();
        for (int offset = 0; offset < 10; offset += 2) {
            var page = service.searchHybrid(new SemanticSearchQuery(SemanticSearchContentType.POST, "free",
                    BLOCKED_USERS, VECTOR, 2, offset, 0.8, 200), keywordQuery);
            assertThat(page.totalElements()).isEqualTo(first.totalElements());
            allPages.addAll(keys(page.results()));
        }
        assertThat(allPages).hasSize(8).doesNotHaveDuplicates().contains("POST:500", "POST:501").doesNotContain("POST:502");
    }

    @ParameterizedTest
    @EnumSource(SemanticSearchContentType.class)
    void exactScanRetainsBoundaryTiesBeforeStableCandidateCap(SemanticSearchContentType type) {
        // No ANN index in this fixture: this verifies exact SQL tie semantics, not ANN recall.
        jdbc.update("INSERT INTO boards VALUES (2, 'ties', 'Tied vectors', 'Y', 'Y', 'Y')");
        jdbc.update("""
                INSERT INTO posts (post_id, board_id, user_id, title, contents, created_at)
                SELECT id, 2, 1, 'same title', 'same body', CURRENT_TIMESTAMP
                FROM generate_series(1001, 1230) AS id
                """);
        jdbc.update("""
                INSERT INTO comments (comment_id, post_id, user_id, content, created_at)
                SELECT id, 1001, 1, 'same comment', CURRENT_TIMESTAMP
                FROM generate_series(1001, 1230) AS id
                """);
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id, embedding)
                SELECT content_type, id, CAST(? AS vector)
                FROM generate_series(1001, 1230) AS id
                CROSS JOIN (VALUES ('POST'), ('COMMENT')) AS types(content_type)
                """, VECTOR);
        String expectedType = type == SemanticSearchContentType.POST ? "POST" : "COMMENT";
        List<String> expected = java.util.stream.IntStream.range(0, 200)
                .mapToObj(index -> expectedType + ":" + (1230 - index)).toList();
        var query = new SemanticSearchQuery(type, "ties", BLOCKED_USERS, VECTOR, 200, 0, 0.2, 200);

        assertThat(keys(vectors.search(query))).containsExactlyElementsOf(expected);
        assertThat(vectors.count(query)).isEqualTo(200);
        List<String> pages = new ArrayList<>();
        for (int offset = 0; offset <= 200; offset += 50) {
            pages.addAll(keys(vectors.search(new SemanticSearchQuery(
                    type, "ties", BLOCKED_USERS, VECTOR, 50, offset, 0.2, 200))));
        }
        assertThat(pages).containsExactlyElementsOf(expected).doesNotHaveDuplicates();
    }

    @Test
    @EnabledIfEnvironmentVariable(named = "POSTGRES_VECTOR_EXPLAIN", matches = "true")
    void vectorCandidatePlanCanUseHnswDistanceOrder() {
        jdbc.execute("CREATE INDEX semantic_visibility_hnsw ON semantic_search_embeddings USING hnsw (embedding vector_cosine_ops) WHERE deleted_at IS NULL");
        jdbc.execute("SET LOCAL enable_seqscan = off");
        jdbc.execute("ANALYZE semantic_search_embeddings");
        var query = vectorQuery(SemanticSearchContentType.POST, 5, 0);
        vectors.configureCandidateSearch(query.candidateLimit());
        assertThat(jdbc.queryForObject("SELECT current_setting('hnsw.ef_search')::integer", Integer.class)).isGreaterThanOrEqualTo(200);
        var plan = new NamedParameterJdbcTemplate(jdbc).queryForList(
                "EXPLAIN (ANALYZE, BUFFERS, FORMAT JSON) " + vectors.searchSql(query), vectors.params(query));
        String output = plan.toString();
        System.out.println("Semantic vector HNSW plan: " + output);
        assertThat(output).contains("semantic_visibility_hnsw");
    }

    private void createTables() {
        jdbc.execute("""
                CREATE TABLE users (
                    user_id BIGINT PRIMARY KEY, status TEXT NOT NULL, deleted_at TIMESTAMP,
                    display_name TEXT NOT NULL, profile_image_url TEXT
                )
                """);
        jdbc.execute("""
                CREATE TABLE boards (
                    board_id BIGINT PRIMARY KEY, board_url TEXT NOT NULL, board_name TEXT NOT NULL,
                    is_active CHAR(1) NOT NULL, is_public CHAR(1) NOT NULL, is_listed CHAR(1)
                )
                """);
        jdbc.execute("""
                CREATE TABLE agents (agent_id BIGINT PRIMARY KEY, name TEXT)
                """);
        jdbc.execute("""
                CREATE TABLE posts (
                    post_id BIGINT PRIMARY KEY, board_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                    agent_id BIGINT, title TEXT, contents TEXT, created_at TIMESTAMP NOT NULL,
                    is_deleted CHAR(1) NOT NULL DEFAULT 'N', is_blinded CHAR(1) NOT NULL DEFAULT 'N',
                    is_secret CHAR(1) NOT NULL DEFAULT 'N'
                )
                """);
        jdbc.execute("""
                CREATE TABLE comments (
                    comment_id BIGINT PRIMARY KEY, post_id BIGINT NOT NULL, user_id BIGINT NOT NULL,
                    agent_id BIGINT, content TEXT, created_at TIMESTAMP NOT NULL,
                    is_deleted CHAR(1) NOT NULL DEFAULT 'N', is_blinded CHAR(1) NOT NULL DEFAULT 'N'
                )
                """);
        jdbc.execute("""
                CREATE TABLE sanctions (
                    target_user_id BIGINT NOT NULL, type TEXT NOT NULL,
                    start_date TIMESTAMP NOT NULL, end_date TIMESTAMP
                )
                """);
        jdbc.execute("""
                CREATE TABLE semantic_search_embeddings (
                    content_type TEXT NOT NULL, content_id BIGINT NOT NULL,
                    embedding vector(1536) NOT NULL, deleted_at TIMESTAMP,
                    PRIMARY KEY (content_type, content_id)
                )
                """);
        // Visibility fixtures contain plain text; preserved HTML decoding is outside this test's contract.
        jdbc.execute("""
                CREATE FUNCTION noviis_expand_preserved_post_html(value TEXT) RETURNS TEXT
                LANGUAGE SQL IMMUTABLE AS 'SELECT value'
                """);
    }

    private void seedAuthorsAndContent() {
        jdbc.update("INSERT INTO boards VALUES (1, 'free', 'Free board', 'Y', 'Y', 'Y')");
        for (long userId = 1; userId <= 12; userId++) {
            jdbc.update("INSERT INTO users (user_id, status, display_name) VALUES (?, 'ACTIVE', ?)",
                    userId, "Author " + userId);
            // Hidden candidates rank above every eligible candidate, making pre-LIMIT filtering observable.
            long rank = VISIBLE_AUTHORS.contains(userId) ? userId : 100 + userId;
            jdbc.update("""
                    INSERT INTO posts (post_id, board_id, user_id, title, contents, created_at)
                    VALUES (?, 1, ?, 'needle title', 'needle body', CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
                    """, userId, userId, rank);
            embed("POST", userId);
            comment(100 + userId, 1, userId, rank);
        }
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE user_id = 4");
        jdbc.update("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE user_id = 5");
        jdbc.update("""
                INSERT INTO sanctions (target_user_id, type, start_date, end_date) VALUES
                    (2, 'BAN', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP + INTERVAL '1 day'),
                    (2, 'ban', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL),
                    (3, 'BAN', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL),
                    (7, 'MUTE', CURRENT_TIMESTAMP - INTERVAL '1 day', NULL),
                    (7, 'MUTE', CURRENT_TIMESTAMP - INTERVAL '2 days', NULL),
                    (8, 'BAN', CURRENT_TIMESTAMP - INTERVAL '2 days', CURRENT_TIMESTAMP - INTERVAL '1 day'),
                    (9, 'BAN', CURRENT_TIMESTAMP + INTERVAL '1 day', NULL),
                    (11, 'BAN', CURRENT_TIMESTAMP, NULL),
                    (12, 'BAN', CURRENT_TIMESTAMP - INTERVAL '1 day', CURRENT_TIMESTAMP)
                """);
        comment(201, 2, 10, 201); // Visible commenter on a banned author's post.
        comment(202, 10, 2, 202); // Banned commenter on a visible author's post.
        comment(203, 10, 10, 203); // Both authors visible.
    }

    private void comment(long commentId, long postId, long userId, long rank) {
        jdbc.update("""
                INSERT INTO comments (comment_id, post_id, user_id, content, created_at)
                VALUES (?, ?, ?, 'needle comment', CURRENT_TIMESTAMP + (? * INTERVAL '1 second'))
                """, commentId, postId, userId, rank);
        embed("COMMENT", commentId);
    }

    private void embed(String contentType, long contentId) {
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id, embedding)
                VALUES (?, ?, CAST(? AS vector))
                """, contentType, contentId, VECTOR);
    }

    private SemanticSearchQuery vectorQuery(SemanticSearchContentType type, int limit, long offset) {
        return new SemanticSearchQuery(type, "free", BLOCKED_USERS, VECTOR, limit, offset);
    }

    private SemanticSearchKeywordQuery keywordQuery(SemanticSearchContentType type, int limit, long offset) {
        return new SemanticSearchKeywordQuery(type, "needle", "free", BLOCKED_USERS, limit, offset);
    }

    private List<String> expectedContent(SemanticSearchContentType type) {
        List<String> expected = new ArrayList<>();
        for (long userId : VISIBLE_AUTHORS) {
            if (type.includesPosts()) {
                expected.add("POST:" + userId);
            }
            if (type.includesComments()) {
                expected.add("COMMENT:" + (100 + userId));
            }
        }
        if (type.includesComments()) {
            expected.add("COMMENT:203");
        }
        return expected;
    }

    private List<String> keys(List<SemanticSearchRow> rows) {
        return rows.stream().map(row -> row.contentType() + ":" + row.contentId()).toList();
    }

    private static String environment(String name, String defaultValue) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? defaultValue : value;
    }
}
