package com.weedrice.whiteboard.domain.search.semantic;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.CsvSource;
import org.junit.jupiter.params.provider.ValueSource;
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
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
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
    private SemanticSearchBackfillService backfill;
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
        backfill = new SemanticSearchBackfillService(jdbc, repository);
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

    private Future<Integer> startBackfillAndAwaitLock(ExecutorService executor, String type, int limit) {
        return startBackfillAndAwaitLock(executor, type, limit, new CompletableFuture<>());
    }

    private Future<Integer> startBackfillAndAwaitLock(ExecutorService executor, String type, int limit,
                                                     CompletableFuture<Integer> backfillPid) {
        var result = executor.submit(() -> worker.execute(tx -> {
            jdbc.execute("SET LOCAL lock_timeout = '10s'");
            backfillPid.complete(backendPid());
            return backfill.enqueueBackfill(type, limit);
        }));
        awaitBlockedByThisTransaction(backfillPid);
        return result;
    }

    @ParameterizedTest
    @CsvSource({"POST,1,2", "COMMENT,11,12"})
    void manualBackfillCountsOnlyTheRepairStillEligibleAfterModeration(
            String type, long moderatedId, long repairId) throws Exception {
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id)
                SELECT 'POST', post_id FROM posts UNION ALL SELECT 'COMMENT', comment_id FROM comments
                """);
        jdbc.update("DELETE FROM semantic_search_embeddings WHERE content_type=? AND content_id IN (?,?)",
                type, moderatedId, repairId);
        LocalDateTime lease = LocalDateTime.of(2026, 11, 20, 9, 45);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = worker.execute(moderation -> {
                String blind = "POST".equals(type)
                        ? "UPDATE posts SET is_blinded='Y' WHERE post_id=?"
                        : "UPDATE comments SET is_blinded='Y' WHERE comment_id=?";
                jdbc.update(blind, moderatedId);
                repository.enqueue(type, moderatedId, SemanticSearchIndexAction.DELETE);
                jdbc.update("""
                        UPDATE semantic_search_jobs SET status='PROCESSING', processing_started_at=?
                        WHERE content_type=? AND content_id=?
                        """, lease, type, moderatedId);

                // Candidate selection read the pre-moderation snapshot; the job key is still held here.
                return startBackfillAndAwaitLock(executor, type, 2);
            });
            assertThat(response.get(10, TimeUnit.SECONDS)).isOne();
        }
        assertThat(jdbc.queryForObject("""
                SELECT processing_started_at FROM semantic_search_jobs
                WHERE content_type=? AND content_id=? AND action='DELETE' AND status='PROCESSING'
                """, LocalDateTime.class, type, moderatedId)).isEqualTo(lease);
        assertThat(action(type, repairId)).isEqualTo("UPSERT");
        assertThat(jobIds(type)).containsExactly(moderatedId, repairId);
    }

    @Test
    void manualBackfillRemovesOnlyItsNewReservationWhenTheParentBecomesPrivate() throws Exception {
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id)
                SELECT 'COMMENT', comment_id FROM comments WHERE comment_id NOT IN (11, 12)
                """);
        repository.enqueue("COMMENT", 11L, SemanticSearchIndexAction.UPSERT);
        jdbc.update("""
                UPDATE semantic_search_jobs SET status='COMPLETED', completed_at=CURRENT_TIMESTAMP
                WHERE content_type='COMMENT' AND content_id=11
                """);
        String previousJob = "SELECT * FROM semantic_search_jobs WHERE content_type='COMMENT' AND content_id=11";
        var original = jdbc.queryForMap(previousJob);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = worker.execute(visibility -> {
                jdbc.queryForMap(previousJob + " FOR UPDATE");
                var result = startBackfillAndAwaitLock(executor, "COMMENT", 2);
                jdbc.update("UPDATE posts SET is_secret='Y' WHERE post_id=1");
                return result;
            });
            assertThat(response.get(10, TimeUnit.SECONDS)).isZero();
        }
        assertThat(jobIds("COMMENT")).containsExactly(11L);
        assertThat(jdbc.queryForMap(previousJob)).isEqualTo(original);
    }

    @Test
    void manualBackfillReservesRepairAndFailedDeleteInCursorOrder() throws Exception {
        repository.enqueue("POST", 1L, SemanticSearchIndexAction.UPSERT);
        jdbc.update("UPDATE semantic_search_jobs SET status='COMPLETED' WHERE content_id=1");
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id=8");
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE content_id=8");
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id)
                SELECT 'POST', post_id FROM posts WHERE post_id<>1
                """);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = worker.execute(cursor -> {
                jdbc.execute("SET LOCAL lock_timeout = '10s'");
                // Pause the cursor at its lower key; simulate the rest of the same page below.
                jdbc.queryForObject("""
                        SELECT job_id FROM semantic_search_jobs
                        WHERE content_type='POST' AND content_id=1 FOR UPDATE
                        """, Long.class);
                var result = startBackfillAndAwaitLock(executor, "POST", 2);
                // If backfill already owns 8, the two transactions now form a lock cycle.
                repository.enqueueReindexPage("POST", List.of(1L, 8L), true);
                return result;
            });
            assertThat(response.get(10, TimeUnit.SECONDS)).isZero();
        }
        assertThat(action("POST", 1L)).isEqualTo("UPSERT");
        assertThat(action("POST", 8L)).isEqualTo("DELETE");
        assertThat(jdbc.queryForObject("SELECT count(*) FROM semantic_search_jobs WHERE status='PENDING'",
                Long.class)).isEqualTo(2L);
    }

    @Test
    void failedDeleteConsumesTheOnlyBackfillSlotAheadOfVisiblePosts() {
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id=8");
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE content_id=8");

        Integer queued = worker.execute(tx -> backfill.enqueueBackfill("POST", 1));

        assertThat(queued).isOne();
        assertThat(jobIds("POST")).containsExactly(8L);
        assertThat(jdbc.queryForObject("""
                SELECT status FROM semantic_search_jobs WHERE content_id=8 AND action='DELETE'
                """, String.class)).isEqualTo("PENDING");
    }

    @Test
    void partialDeleteBatchFillsItsLastSlotWithTheOldestRepair() {
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id=8");
        jdbc.update("UPDATE posts SET created_at=TIMESTAMP '2000-01-01' WHERE post_id=4");
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE content_id=8");

        Integer queued = worker.execute(tx -> backfill.enqueueBackfill("POST", 2));

        assertThat(queued).isEqualTo(2);
        assertThat(jobIds("POST")).containsExactly(4L, 8L);
        assertThat(action("POST", 4L)).isEqualTo("UPSERT");
        assertThat(action("POST", 8L)).isEqualTo("DELETE");
    }

    @Test
    void partialDeleteBatchFinishesWhileTheRepairOutsideItsRemainingLimitIsLocked() {
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id=8");
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        repository.enqueue("POST", 2L, SemanticSearchIndexAction.UPSERT);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE content_id=8");
        jdbc.update("UPDATE semantic_search_jobs SET status='COMPLETED' WHERE content_id=2");

        try (var executor = Executors.newSingleThreadExecutor()) {
            worker.executeWithoutResult(other -> {
                // Repair 1 is older in the queue order. The single free slot never needs key 2.
                repository.reserveJobKeys("POST", List.of(2L));
                var result = executor.submit(() -> worker.execute(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    return backfill.enqueueBackfill("POST", 2);
                }));
                assertThat(result).succeedsWithin(5, TimeUnit.SECONDS).isEqualTo(2);
            });
        }
        assertThat(jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs WHERE status='PENDING' ORDER BY content_id
                """, Long.class)).containsExactly(1L, 8L);
        assertThat(action("POST", 1L)).isEqualTo("UPSERT");
        assertThat(action("POST", 8L)).isEqualTo("DELETE");
        assertThat(jdbc.queryForObject("""
                SELECT status FROM semantic_search_jobs WHERE content_id=2
                """, String.class)).isEqualTo("COMPLETED");
    }

    @Test
    void repairCandidateThatBecomesAFailedDeleteWhileWaitingIsRedrivenAsADelete() throws Exception {
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id)
                SELECT 'POST', post_id FROM posts WHERE post_id NOT IN (1,2)
                """);
        repository.enqueue("POST", 1L, SemanticSearchIndexAction.UPSERT);
        repository.enqueue("POST", 2L, SemanticSearchIndexAction.UPSERT);
        jdbc.update("UPDATE semantic_search_jobs SET status='COMPLETED'");
        long jobId = jdbc.queryForObject("SELECT job_id FROM semantic_search_jobs WHERE content_id=2", Long.class);
        LocalDateTime lease = LocalDateTime.of(2026, 11, 20, 10, 30);

        try (var executor = Executors.newSingleThreadExecutor()) {
            var response = worker.execute(barrier -> {
                repository.reserveJobKeys("POST", List.of(1L));
                var result = startBackfillAndAwaitLock(executor, "POST", 2);
                worker.executeWithoutResult(moderation -> {
                    jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id=2");
                    repository.enqueue("POST", 2L, SemanticSearchIndexAction.DELETE);
                });
                worker.executeWithoutResult(claim -> assertThat(repository.claimForProcessing(jobId, 1, lease)).isOne());
                worker.executeWithoutResult(failure -> assertThat(repository.markFailedIfCurrent(
                        jobId, lease, 1, "delete failed", true, lease.plusMinutes(1))).isOne());
                return result;
            });
            assertThat(response.get(10, TimeUnit.SECONDS)).isEqualTo(2);
        }
        assertThat(action("POST", 1L)).isEqualTo("UPSERT");
        assertThat(jdbc.queryForObject("""
                SELECT retry_count FROM semantic_search_jobs
                WHERE content_id=2 AND status='PENDING' AND action='DELETE'
                """, Integer.class)).isZero();
    }

    @Test
    void concurrentDeleteOnlyBackfillCompletesBeforeTheFirstRequestCommits() {
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id IN (1,8)");
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        repository.enqueue("POST", 1L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED'");

        try (var executor = Executors.newSingleThreadExecutor()) {
            worker.executeWithoutResult(first -> {
                assertThat(backfill.enqueueBackfill("POST", 1)).isOne();
                assertThat(jdbc.queryForList("""
                        SELECT content_id FROM semantic_search_jobs WHERE status='PENDING'
                        """, Long.class)).containsExactly(8L);

                var result = executor.submit(() -> worker.execute(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    return backfill.enqueueBackfill("POST", 1);
                }));
                assertThat(result).succeedsWithin(5, TimeUnit.SECONDS).isEqualTo(1);
            });
        }
        assertThat(jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs
                WHERE content_type='POST' AND status='PENDING' AND action='DELETE' ORDER BY content_id
                """, Long.class)).containsExactly(1L, 8L);
    }

    @ParameterizedTest
    @ValueSource(ints = {1, 2})
    void concurrentBackfillFillsSlotsSkippedByALockedDeleteWithVisiblePostRepairs(int limit) {
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id=8");
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE content_id=8");

        try (var executor = Executors.newSingleThreadExecutor()) {
            worker.executeWithoutResult(first -> {
                assertThat(backfill.enqueueBackfill("POST", 1)).isOne();
                var result = executor.submit(() -> worker.execute(tx -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    return backfill.enqueueBackfill("POST", limit);
                }));
                assertThat(result).succeedsWithin(5, TimeUnit.SECONDS).isEqualTo(limit);
                assertThat(jdbc.queryForList("""
                        SELECT content_id FROM semantic_search_jobs
                        WHERE status='PENDING' AND action='UPSERT' ORDER BY content_id
                        """, Long.class)).containsExactlyElementsOf(List.of(1L, 2L).subList(0, limit));
            });
        }
        assertThat(action("POST", 8L)).isEqualTo("DELETE");
    }

    @Test
    void backfillWithAPartiallyLockedDeletePageReservesTheLowerRepairKeyBeforeTheHigherDelete() {
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id IN (7,8)");
        repository.enqueue("POST", 7L, SemanticSearchIndexAction.DELETE);
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE action='DELETE'");
        repository.enqueue("POST", 1L, SemanticSearchIndexAction.UPSERT);
        jdbc.update("UPDATE semantic_search_jobs SET status='COMPLETED' WHERE content_id=1");
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id)
                SELECT 'POST', post_id FROM posts WHERE post_id<>1
                """);

        try (var executor = Executors.newSingleThreadExecutor()) {
            worker.executeWithoutResult(first -> {
                assertThat(backfill.enqueueBackfill("POST", 1)).isOne();
                assertThat(jdbc.queryForObject("""
                        SELECT content_id FROM semantic_search_jobs WHERE status='PENDING'
                        """, Long.class)).isEqualTo(7L);
                var second = worker.execute(cursor -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    repository.reserveJobKeys("POST", List.of(1L));
                    var result = startBackfillAndAwaitLock(executor, "POST", 2);
                    // The cursor can finish acquiring its page only if the waiting request released key 8.
                    repository.reserveJobKeys("POST", List.of(1L, 8L));
                    return result;
                });
                assertThat(second).succeedsWithin(5, TimeUnit.SECONDS).isEqualTo(2);
            });
        }
        assertThat(jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs WHERE status='PENDING' AND action='DELETE' ORDER BY content_id
                """, Long.class)).containsExactly(7L, 8L);
        assertThat(jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs WHERE status='PENDING' AND action='UPSERT'
                """, Long.class)).containsExactly(1L);
    }

    @Test
    void expandedBackfillReleasesItsHigherRepairKeyBeforeWaitingForTheLowerKey() throws Exception {
        jdbc.update("UPDATE posts SET created_at=TIMESTAMP '2000-01-01' WHERE post_id=8");
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id=7");
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id)
                SELECT 'POST', post_id FROM posts WHERE post_id NOT IN (1,8)
                """);
        repository.enqueueAll("POST", List.of(1L, 8L), SemanticSearchIndexAction.UPSERT);
        jdbc.update("UPDATE semantic_search_jobs SET status='COMPLETED'");
        repository.enqueue("POST", 7L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE content_id=7");

        try (var executor = Executors.newSingleThreadExecutor()) {
            var result = worker.execute(cursor -> {
                jdbc.execute("SET LOCAL lock_timeout = '10s'");
                repository.reserveJobKeys("POST", List.of(1L));
                var backfillPid = new CompletableFuture<Integer>();
                var mixed = worker.execute(barrier -> {
                    repository.reserveJobKeys("POST", List.of(8L));
                    // The observed DELETE leaves one repair slot, which the older post 8 wins.
                    var request = startBackfillAndAwaitLock(executor, "POST", 2, backfillPid);
                    Integer redriven = worker.execute(admin -> backfill.enqueueBackfill("POST", 1));
                    assertThat(redriven).isOne();
                    return request;
                });
                // The same backend now needs both repairs and is waiting on this cursor's lower key.
                awaitBlockedByThisTransaction(backfillPid);
                repository.reserveJobKeys("POST", List.of(8L));
                return mixed;
            });
            assertThat(result.get(10, TimeUnit.SECONDS)).isEqualTo(2);
        }
        assertThat(jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs WHERE status='PENDING' AND action='UPSERT' ORDER BY content_id
                """, Long.class)).containsExactly(1L, 8L);
        assertThat(jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs WHERE status='PENDING' AND action='DELETE'
                """, Long.class)).containsExactly(7L);
    }

    @Test
    void mixedBackfillReplacesADeleteClaimedByAnotherRequestWithANewlyFailedDelete() throws Exception {
        jdbc.update("UPDATE posts SET is_blinded='Y' WHERE post_id IN (2,8)");
        jdbc.update("""
                INSERT INTO semantic_search_embeddings (content_type, content_id)
                SELECT 'POST', post_id FROM posts WHERE post_id<>1
                """);
        repository.enqueue("POST", 1L, SemanticSearchIndexAction.UPSERT);
        jdbc.update("UPDATE semantic_search_jobs SET status='COMPLETED' WHERE content_id=1");
        repository.enqueue("POST", 8L, SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE semantic_search_jobs SET status='FAILED' WHERE content_id=8");
        repository.enqueue("POST", 2L, SemanticSearchIndexAction.DELETE);
        long processingJob = jdbc.queryForObject(
                "SELECT job_id FROM semantic_search_jobs WHERE content_id=2", Long.class);
        LocalDateTime lease = LocalDateTime.of(2026, 11, 20, 10, 15);
        assertThat(repository.claimForProcessing(processingJob, 5, lease)).isOne();

        try (var executor = Executors.newSingleThreadExecutor()) {
            var second = worker.execute(first -> {
                assertThat(backfill.enqueueBackfill("POST", 1)).isOne();
                // Hold the candidate repair until DELETE 2 fails; DELETE 8 belongs to this request.
                repository.reserveJobKeys("POST", List.of(1L));
                var result = startBackfillAndAwaitLock(executor, "POST", 2);
                assertThat(repository.markFailedIfCurrent(processingJob, lease, 5, "failure", false, null)).isOne();
                return result;
            });
            assertThat(second.get(10, TimeUnit.SECONDS)).isEqualTo(2);
        }
        assertThat(action("POST", 1L)).isEqualTo("UPSERT");
        assertThat(jdbc.queryForList("""
                SELECT content_id FROM semantic_search_jobs
                WHERE status='PENDING' AND action='DELETE' ORDER BY content_id
                """, Long.class)).containsExactly(2L, 8L);
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
