package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.core.io.ClassPathResource;
import org.springframework.jdbc.datasource.DataSourceTransactionManager;
import org.springframework.jdbc.datasource.DriverManagerDataSource;
import org.springframework.jdbc.datasource.init.ResourceDatabasePopulator;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

/** Exercises committed worker transactions in a private schema using the production cursor migrations. */
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
class SemanticSearchAuthorRestorePostgresTest {

    private String schema;
    private JdbcTemplate admin;
    private JdbcTemplate jdbc;
    private SemanticSearchJobRepository repository;
    private SemanticSearchReindexRepository cursors;
    private SemanticSearchReindexService service;
    private TransactionTemplate worker;
    private SimpleMeterRegistry meters;

    @BeforeEach
    void createIsolatedFixtures() {
        DriverManagerDataSource dataSource = new DriverManagerDataSource(
                environment("POSTGRES_SMOKE_DATASOURCE_URL", "jdbc:postgresql://localhost:5432/noviis_ci_app"),
                environment("POSTGRES_SMOKE_DATASOURCE_USERNAME", "noviis_ci"),
                environment("POSTGRES_SMOKE_DATASOURCE_PASSWORD", "noviis_ci"));
        schema = "semantic_author_restore_" + UUID.randomUUID().toString().replace("-", "");
        admin = new JdbcTemplate(dataSource);
        admin.execute("CREATE SCHEMA " + schema);
        dataSource.setSchema(schema);
        jdbc = new JdbcTemplate(dataSource);
        new ResourceDatabasePopulator(
                new ClassPathResource("db/migration/V69__semantic_reindex_cursor_jobs.sql"),
                new ClassPathResource("db/migration/V71__semantic_reindex_retry_state.sql"),
                new ClassPathResource("db/migration/V103__semantic_author_reindex_jobs.sql")
        ).execute(dataSource);
        jdbc.execute("CREATE TABLE boards (board_id BIGINT PRIMARY KEY, is_active CHAR(1), is_public CHAR(1))");
        jdbc.execute("CREATE TABLE users (user_id BIGINT PRIMARY KEY, status VARCHAR(16), deleted_at TIMESTAMP)");
        jdbc.update("INSERT INTO boards SELECT n, 'Y', 'Y' FROM generate_series(10,60,10) n");
        jdbc.update("INSERT INTO users SELECT n, 'ACTIVE', NULL FROM generate_series(7,9) n");
        jdbc.execute("""
                CREATE TABLE posts (
                    post_id BIGINT PRIMARY KEY, board_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL, is_deleted CHAR(1) NOT NULL,
                    is_blinded CHAR(1) NOT NULL DEFAULT 'N', is_secret CHAR(1) NOT NULL DEFAULT 'N',
                    created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE comments (
                    comment_id BIGINT PRIMARY KEY, post_id BIGINT NOT NULL,
                    user_id BIGINT NOT NULL, is_deleted CHAR(1) NOT NULL,
                    is_blinded CHAR(1) NOT NULL DEFAULT 'N', created_at TIMESTAMP NOT NULL DEFAULT CURRENT_TIMESTAMP)
                """);
        jdbc.execute("""
                CREATE TABLE semantic_search_embeddings (
                    embedding_id BIGSERIAL PRIMARY KEY, content_type VARCHAR(20) NOT NULL,
                    content_id BIGINT NOT NULL, deleted_at TIMESTAMP, UNIQUE(content_type, content_id))
                """);
        jdbc.execute("""
                CREATE TABLE semantic_search_jobs (
                    job_id BIGSERIAL PRIMARY KEY,
                    content_type VARCHAR(20) NOT NULL, content_id BIGINT NOT NULL,
                    action VARCHAR(20) NOT NULL, status VARCHAR(20) NOT NULL, retry_count INTEGER NOT NULL,
                    queued_at TIMESTAMP NOT NULL, next_attempt_at TIMESTAMP,
                    processing_started_at TIMESTAMP, completed_at TIMESTAMP, last_error_message TEXT,
                    created_at TIMESTAMP NOT NULL, updated_at TIMESTAMP NOT NULL,
                    UNIQUE(content_type, content_id))
                """);
        jdbc.update("""
                INSERT INTO posts VALUES
                    (1,10,7,'N'), (2,10,7,'N'), (3,20,8,'N'), (4,30,8,'N'),
                    (5,40,7,'Y'), (6,50,8,'Y'), (7,60,8,'N'), (8,10,9,'N')
                """);
        jdbc.update("""
                INSERT INTO comments VALUES
                    (11,1,7,'N'), (12,1,9,'N'), (13,3,7,'N'),
                    (14,4,8,'N'), (15,6,7,'N'), (16,7,7,'Y'), (17,8,9,'N'),
                    (18,2,8,'Y'), (19,5,8,'N'), (20,3,9,'N')
                """);
        worker = new TransactionTemplate(new DataSourceTransactionManager(dataSource));
        worker.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        LocalDateTime dbTime = jdbc.queryForObject("SELECT LOCALTIMESTAMP", LocalDateTime.class);
        Clock clock = Clock.fixed(dbTime.plusMinutes(1).toInstant(ZoneOffset.UTC), ZoneOffset.UTC);
        repository = new SemanticSearchJobRepository(jdbc, clock);
        cursors = new SemanticSearchReindexRepository(jdbc);
        meters = new SimpleMeterRegistry();
        service = new SemanticSearchReindexService(cursors, repository, worker, clock,
                new SemanticSearchReindexMetrics(meters));
    }

    @AfterEach
    void dropIsolatedFixtures() {
        if (meters != null) meters.close();
        if (admin != null) admin.execute("DROP SCHEMA IF EXISTS " + schema + " CASCADE");
    }

    @Test
    void restorationQueuesOneCursorThenOnlyRelatedContent() {
        service.enqueueAuthorContent(7L);
        service.enqueueAuthorContent(7L);

        assertThat(cursors.countStatus("PENDING")).isOne();
        assertThat(jobIds("POST")).isEmpty();
        assertThat(jobIds("COMMENT")).isEmpty();
        assertThat(service.processPages()).isOne();
        assertThat(service.processPages()).isOne();
        assertThat(cursors.countStatus("COMPLETED")).isOne();
        assertThat(jobIds("POST")).containsExactly(1L, 2L);
        assertThat(jobIds("COMMENT")).containsExactly(11L, 12L, 13L);

        service.enqueueAuthorContent(9L);
        service.enqueueAuthorContent(7L);
        assertThat(service.processPages()).isEqualTo(2);
        assertThat(service.processPages()).isEqualTo(2);
        assertThat(jobIds("POST")).containsExactly(1L, 2L, 8L);
        assertThat(jobIds("COMMENT")).containsExactly(11L, 12L, 13L, 17L, 20L);
    }

    @Test
    void pageRechecksVisibilityAndPreservesConcurrentDelete() {
        worker.executeWithoutResult(read -> {
            List<Long> ids = repository.findAuthorPageIds("COMMENT", 7L, 0, 500);
            worker.executeWithoutResult(moderation -> {
                jdbc.update("UPDATE comments SET is_blinded='Y' WHERE comment_id=12");
                repository.enqueue("COMMENT", 12L, SemanticSearchIndexAction.DELETE);
            });
            repository.enqueueReindexPage("COMMENT", ids, false);
        });
        assertThat(action("COMMENT", 12L)).isEqualTo("DELETE");
        assertThat(action("COMMENT", 13L)).isEqualTo("UPSERT");
    }

    @Test
    void authorPageAllowsSourceEditsAndOrdersTheSubsequentDeleteAfterItsOwnWork() throws Exception {
        try (var executor = Executors.newSingleThreadExecutor()) {
            var moderation = worker.execute(page -> {
                repository.enqueueReindexPage("COMMENT", List.of(11L, 12L, 13L), false);
                var sourceUpdated = new CompletableFuture<Integer>();
                var edit = executor.submit(() -> worker.executeWithoutResult(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    jdbc.queryForList("SELECT user_id FROM users WHERE user_id=9 FOR UPDATE");
                    jdbc.queryForList("SELECT board_id FROM boards WHERE board_id=10 FOR UPDATE");
                    jdbc.queryForList("SELECT post_id FROM posts WHERE post_id=1 FOR UPDATE");
                    jdbc.update("UPDATE comments SET is_blinded='Y' WHERE comment_id=12");
                    sourceUpdated.complete(backendPid());
                    repository.enqueue("COMMENT", 12L, SemanticSearchIndexAction.DELETE);
                }));
                awaitBlockedByThisTransaction(sourceUpdated);
                return edit;
            });
            moderation.get(10, TimeUnit.SECONDS);
        }
        assertThat(action("COMMENT", 12L)).isEqualTo("DELETE");
    }

    @Test
    void authorPageWaitingForModerationReadsVisibilityAfterAcquiringTheJobKey() throws Exception {
        repository.enqueue("COMMENT", 12L, SemanticSearchIndexAction.UPSERT);
        LocalDateTime lease = LocalDateTime.of(2026, 11, 20, 9, 30);
        try (var executor = Executors.newSingleThreadExecutor()) {
            var reindex = worker.execute(moderation -> {
                jdbc.update("UPDATE comments SET is_blinded='Y' WHERE comment_id=12");
                repository.enqueue("COMMENT", 12L, SemanticSearchIndexAction.DELETE);
                jdbc.update("""
                        UPDATE semantic_search_jobs SET status='PROCESSING', processing_started_at=?
                        WHERE content_type='COMMENT' AND content_id=12
                        """, lease);
                var pageStarted = new CompletableFuture<Integer>();
                var page = executor.submit(() -> worker.executeWithoutResult(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    pageStarted.complete(backendPid());
                    repository.enqueueReindexPage("COMMENT", List.of(12L), false);
                }));
                awaitBlockedByThisTransaction(pageStarted);
                return page;
            });
            reindex.get(10, TimeUnit.SECONDS);
        }
        assertThat(action("COMMENT", 12L)).isEqualTo("DELETE");
        assertThat(jdbc.queryForObject("""
                SELECT processing_started_at FROM semantic_search_jobs
                WHERE content_type='COMMENT' AND content_id=12 AND status='PROCESSING'
                """, LocalDateTime.class)).isEqualTo(lease);
    }

    @Test
    void boardAndPostCommentPagesKeepLeasedDeletesAndRestoreVisibleContent() {
        jdbc.update("UPDATE posts SET is_secret='Y' WHERE post_id=2");
        jdbc.update("UPDATE comments SET is_blinded='Y' WHERE comment_id=12");
        repository.enqueue("COMMENT", 12L, SemanticSearchIndexAction.DELETE);
        jdbc.update("""
                UPDATE semantic_search_jobs SET status='PROCESSING', processing_started_at=CURRENT_TIMESTAMP
                WHERE content_type='COMMENT' AND content_id=12
                """);
        service.enqueueBoard(10L);
        service.enqueuePostComments(1L);
        assertThat(service.processPages()).isEqualTo(2);
        assertThat(service.processPages()).isOne();
        assertThat(action("POST", 2L)).isEqualTo("DELETE");
        assertThat(action("COMMENT", 12L)).isEqualTo("DELETE");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM semantic_search_jobs WHERE content_type='COMMENT' AND content_id=12
                """, String.class)).isEqualTo("PROCESSING");
        assertThat(action("COMMENT", 11L)).isEqualTo("UPSERT");

        jdbc.update("UPDATE posts SET is_secret='N' WHERE post_id=2");
        jdbc.update("UPDATE comments SET is_blinded='N' WHERE comment_id=12");
        service.enqueueBoard(10L);
        service.enqueuePostComments(1L);
        assertThat(service.processPages()).isEqualTo(2);
        assertThat(service.processPages()).isOne();
        assertThat(action("POST", 2L)).isEqualTo("UPSERT");
        assertThat(action("COMMENT", 12L)).isEqualTo("UPSERT");
    }

    private int backendPid() {
        return jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
    }

    private void awaitBlockedByThisTransaction(CompletableFuture<Integer> otherPid) {
        int owner = backendPid();
        await().pollInSameThread().atMost(5, TimeUnit.SECONDS).until(() -> otherPid.isDone()
                && Boolean.TRUE.equals(jdbc.queryForObject(
                        "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, owner, otherPid.join())));
    }

    @Test
    void emptyVisiblePageStillAdvancesToTheNextCandidatePage() {
        jdbc.update("UPDATE posts SET is_secret='Y' WHERE user_id=7");
        jdbc.update("""
                INSERT INTO posts (post_id,board_id,user_id,is_deleted,is_blinded)
                SELECT n,10,7,'N','Y' FROM generate_series(1000,1499) n
                """);
        jdbc.update("INSERT INTO posts VALUES (1500,10,7,'N')");
        service.enqueueAuthorContent(7L);
        assertThat(service.processPages()).isOne();
        assertThat(jobIds("POST")).isEmpty();
        assertThat(jdbc.queryForObject("SELECT last_content_id FROM semantic_search_author_reindex_jobs",
                Long.class)).isEqualTo(1497L);
        assertThat(service.processPages()).isOne();
        assertThat(jobIds("POST")).containsExactly(1500L);
        assertThat(service.processPages()).isOne();
        assertThat(cursors.countStatus("COMPLETED")).isOne();
    }

    @Test
    void redriveRecoverAndRestartRespectAuthorLease() {
        service.enqueueAuthorContent(7L);
        LocalDateTime now = jdbc.queryForObject("SELECT LOCALTIMESTAMP", LocalDateTime.class).plusMinutes(1);
        Long id = cursors.findRunnableIds(now, 1).getFirst();
        UUID lease = UUID.randomUUID();
        assertThat(cursors.claim(id, lease, now)).isOne();
        assertThat(cursors.recoverStale(now.plusMinutes(1), 5, now.plusMinutes(2), now, "expired")).isOne();
        assertThat(cursors.advance(id, lease, "COMMENT", 2, true, now)).isZero();
        assertThat(cursors.findRunnableIds(now, 1)).isEmpty();
        service.enqueueAuthorContent(7L);
        assertThat(cursors.claim(id, lease, now)).isOne();
        assertThat(cursors.markFailed(id, lease, 1, now, now, "failed")).isOne();
        assertThat(cursors.countStatus("FAILED")).isOne();
        service.redrive(id);
        assertThat(cursors.countStatus("PENDING")).isOne();
    }

    private String action(String type, long id) {
        return jdbc.queryForObject("SELECT action FROM semantic_search_jobs WHERE content_type=? AND content_id=?",
                String.class, type, id);
    }

    private List<Long> jobIds(String contentType) {
        return jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs WHERE content_type=? ORDER BY content_id
                """, Long.class, contentType);
    }

    private static String environment(String name, String fallback) {
        String value = System.getenv(name);
        return value == null || value.isBlank() ? fallback : value;
    }
}
