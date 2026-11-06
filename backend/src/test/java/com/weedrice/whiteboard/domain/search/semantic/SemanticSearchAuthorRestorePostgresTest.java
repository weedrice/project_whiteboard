package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.SQLException;
import java.time.Clock;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;

/** Exercises the production scope-selection and durable enqueue SQL in a rolled-back private schema. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
class SemanticSearchAuthorRestorePostgresTest {

    private Connection connection;
    private JdbcTemplate jdbc;
    private SemanticSearchReindexRepository repository;

    @BeforeEach
    void createIsolatedFixtures() throws SQLException {
        connection = DriverManager.getConnection(
                environment("POSTGRES_SMOKE_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/noviis_ci_app"),
                environment("POSTGRES_SMOKE_DATASOURCE_USERNAME", "noviis_ci"),
                environment("POSTGRES_SMOKE_DATASOURCE_PASSWORD", "noviis_ci"));
        connection.setAutoCommit(false);
        jdbc = new JdbcTemplate(new SingleConnectionDataSource(connection, true));
        String schema = "semantic_author_restore_" + UUID.randomUUID().toString().replace("-", "");
        jdbc.execute("CREATE SCHEMA " + schema);
        jdbc.execute("SET LOCAL search_path TO " + schema + ", public");
        jdbc.execute("""
                CREATE TABLE posts (
                    post_id BIGINT PRIMARY KEY, board_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL, is_deleted CHAR(1) NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE comments (
                    comment_id BIGINT PRIMARY KEY, post_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL, is_deleted CHAR(1) NOT NULL)
                """);
        jdbc.execute("""
                CREATE TABLE semantic_search_reindex_jobs (
                    reindex_job_id BIGSERIAL PRIMARY KEY,
                    scope_type VARCHAR(30) NOT NULL, scope_id BIGINT NOT NULL,
                    content_phase VARCHAR(20) NOT NULL, last_content_id BIGINT NOT NULL,
                    status VARCHAR(20) NOT NULL, retry_count INTEGER NOT NULL DEFAULT 0,
                    next_attempt_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP,
                    last_error TEXT, lease_token UUID, failed_at TIMESTAMP,
                    processing_started_at TIMESTAMP, created_at TIMESTAMP, updated_at TIMESTAMP,
                    UNIQUE(scope_type, scope_id))
                """);
        jdbc.update("""
                INSERT INTO posts VALUES
                    (1,10,7,'N'), (2,10,7,'N'), (3,20,8,'N'), (4,30,8,'N'),
                    (5,40,7,'Y'), (6,50,8,'Y'), (7,60,8,'N')
                """);
        jdbc.update("""
                INSERT INTO comments VALUES
                    (11,1,7,'N'), (12,1,9,'N'), (13,3,7,'N'),
                    (14,4,8,'N'), (15,6,7,'N'), (16,7,7,'Y')
                """);
        repository = new SemanticSearchReindexRepository(jdbc);
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
    void restoreSelectsAuthoredPostAndCommentBoardsAndRestartsExistingCursors() {
        assertThat(repository.findBoardIdsForAuthorContent(7L)).containsExactly(10L, 20L);
        assertThat(repository.findBoardIdsForAuthorContent(99L)).isEmpty();
        SimpleMeterRegistry meters = new SimpleMeterRegistry();
        try {
            SemanticSearchReindexService service = new SemanticSearchReindexService(
                    repository, mock(SemanticSearchJobRepository.class), mock(TransactionTemplate.class),
                    Clock.systemUTC(), new SemanticSearchReindexMetrics(meters));

            service.enqueueAuthorContent(7L);
            jdbc.update("""
                    UPDATE semantic_search_reindex_jobs
                    SET status='COMPLETED', content_phase='COMMENT', last_content_id=999
                    WHERE scope_id=10
                    """);
            service.enqueueAuthorContent(7L);
            service.enqueueAuthorContent(99L);

            assertThat(jdbc.queryForList("""
                    SELECT scope_id FROM semantic_search_reindex_jobs
                    WHERE scope_type='BOARD' AND content_phase='POST' AND last_content_id=0
                      AND status='PENDING' AND retry_count=0
                    ORDER BY scope_id
                    """, Long.class)).containsExactly(10L, 20L);
            assertThat(jdbc.queryForObject("SELECT count(*) FROM semantic_search_reindex_jobs", Long.class))
                    .isEqualTo(2L);
        } finally {
            meters.close();
        }
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
