package com.weedrice.whiteboard.domain.agent.repository;

import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.data.jpa.repository.Query;
import org.springframework.jdbc.core.namedparam.NamedParameterJdbcTemplate;
import org.springframework.jdbc.datasource.SingleConnectionDataSource;

import java.sql.Connection;
import java.sql.DriverManager;
import java.sql.Timestamp;
import java.time.LocalDateTime;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import static org.assertj.core.api.Assertions.assertThat;

/** Disposable PostgreSQL only; never point AGENT_ACTIVITY_TEST_JDBC_URL at an application DB. */
@EnabledIfEnvironmentVariable(named = "AGENT_ACTIVITY_TEST_JDBC_URL", matches = ".+")
class AgentActivityPostgresTest {
    private String schema;
    private String upsert;

    @BeforeEach
    void createIsolatedSchema() throws Exception {
        schema = "agent_activity_" + UUID.randomUUID().toString().replace("-", "");
        upsert = AgentPostActivityReadRepository.class.getMethod("upsertLastReadAt", Long.class, Long.class, LocalDateTime.class)
                .getAnnotation(Query.class).value();
        try (Connection connection = connect()) {
            connection.createStatement().execute("CREATE SCHEMA " + schema);
            connection.setSchema(schema);
            connection.createStatement().execute("CREATE TABLE posts(post_id BIGINT PRIMARY KEY)");
            connection.createStatement().execute("INSERT INTO posts VALUES(10)");
            connection.createStatement().execute("CREATE TABLE comments(comment_id BIGSERIAL PRIMARY KEY, post_id BIGINT, created_at TIMESTAMP NOT NULL)");
            connection.createStatement().execute("""
                    CREATE TABLE agent_post_activity_reads (
                        agent_id BIGINT NOT NULL, post_id BIGINT NOT NULL, last_read_at TIMESTAMP NOT NULL,
                        created_at TIMESTAMP NOT NULL, modified_at TIMESTAMP NOT NULL,
                        CONSTRAINT uk_agent_post_activity_reads_agent_post UNIQUE(agent_id, post_id))
                    """);
        }
    }

    @AfterEach
    void dropIsolatedSchema() throws Exception {
        try (Connection connection = connect()) {
            connection.createStatement().execute("DROP SCHEMA " + schema + " CASCADE");
        }
    }

    @Test
    void nativeUpsertKeepsStoredCursorMonotonicAtMicrosecondPrecision() throws Exception {
        try (Connection connection = inSchema()) {
            LocalDateTime at = LocalDateTime.of(2026, 11, 5, 12, 0, 0, 123456000);
            upsert(connection, at);
            upsert(connection, at.minusNanos(1000));
            try (var result = connection.createStatement().executeQuery("SELECT last_read_at FROM agent_post_activity_reads")) {
                result.next();
                assertThat(result.getTimestamp(1).toLocalDateTime()).isEqualTo(at);
            }
        }
    }

    @Test
    void postLockKeepsCommentCreatedDuringAcknowledgementAfterObservedBoundary() throws Exception {
        var executor = Executors.newSingleThreadExecutor();
        try (Connection owner = inSchema(); Connection writer = inSchema()) {
            owner.setAutoCommit(false);
            writer.setAutoCommit(false);
            owner.createStatement().execute("SELECT post_id FROM posts WHERE post_id=10 FOR UPDATE");
            LocalDateTime observed;
            try (var result = owner.createStatement().executeQuery("SELECT clock_timestamp()::timestamp")) {
                result.next(); observed = result.getTimestamp(1).toLocalDateTime();
            }
            upsert(owner, observed);
            int writerPid;
            try (var result = writer.createStatement().executeQuery("SELECT pg_backend_pid()")) {
                result.next(); writerPid = result.getInt(1);
            }
            var created = executor.submit(() -> {
                var lock = writer.createStatement();
                lock.setQueryTimeout(10);
                lock.execute("SELECT post_id FROM posts WHERE post_id=10 FOR UPDATE");
                writer.createStatement().execute("INSERT INTO comments(post_id, created_at) VALUES(10, clock_timestamp())");
                writer.commit();
                return true;
            });
            try {
                boolean waiting = false;
                long deadline = System.nanoTime() + TimeUnit.SECONDS.toNanos(5);
                while (System.nanoTime() < deadline && !waiting) {
                    try (Connection monitor = inSchema(); var result = monitor.createStatement().executeQuery(
                            "SELECT wait_event_type FROM pg_stat_activity WHERE pid=" + writerPid)) {
                        waiting = result.next() && "Lock".equals(result.getString(1));
                    }
                    if (!waiting) Thread.sleep(25);
                }
                assertThat(waiting).as("concurrent comment writer must wait for the acknowledged post lock").isTrue();
                assertThat(created.isDone()).isFalse();
                owner.commit();
                assertThat(created.get(10, TimeUnit.SECONDS)).isTrue();
                try (var result = owner.createStatement().executeQuery("""
                        SELECT COUNT(*) FROM comments c JOIN agent_post_activity_reads r ON r.post_id=c.post_id
                        WHERE c.created_at > r.last_read_at
                        """)) {
                    result.next(); assertThat(result.getLong(1)).isEqualTo(1);
                }
            } finally {
                owner.rollback();
            }
        } finally {
            executor.shutdownNow();
            executor.awaitTermination(10, TimeUnit.SECONDS);
        }
    }

    private Connection connect() throws Exception {
        return DriverManager.getConnection(System.getenv("AGENT_ACTIVITY_TEST_JDBC_URL"),
                System.getenv("AGENT_ACTIVITY_TEST_DB_USER"), System.getenv("AGENT_ACTIVITY_TEST_DB_PASSWORD"));
    }

    private Connection inSchema() throws Exception {
        Connection connection = connect();
        connection.setSchema(schema);
        return connection;
    }

    private void upsert(Connection connection, LocalDateTime at) {
        new NamedParameterJdbcTemplate(new SingleConnectionDataSource(connection, true)).update(upsert,
                Map.of("agentId", 7L, "postId", 10L, "lastReadAt", Timestamp.valueOf(at)));
    }
}
