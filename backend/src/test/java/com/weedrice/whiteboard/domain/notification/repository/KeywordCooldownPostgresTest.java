package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import com.weedrice.whiteboard.domain.notification.entity.UserKeywordSubscription;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

import java.sql.SQLException;
import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Import(QuerydslConfig.class)
class KeywordCooldownPostgresTest {
    @Autowired private UserKeywordSubscriptionRepository subscriptions;
    @Autowired private KeywordNotificationFanoutJobRepository fanouts;
    @Autowired private EntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PlatformTransactionManager transactionManager;

    @Test
    void overlappingPostJobsMustReadTheCommittedSubscriptionCooldown() {
        TransactionTemplate worker = new TransactionTemplate(transactionManager);
        worker.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        String keyword = "cooldown-" + UUID.randomUUID().toString().substring(0, 8);
        Long userId = worker.execute(status -> {
            User user = User.builder().loginId(keyword).displayName(keyword)
                    .email(keyword + "@example.org").password("test-password").build();
            entityManager.persist(user);
            entityManager.persist(UserKeywordSubscription.builder().user(user).keyword(keyword).build());
            return user.getUserId();
        });

        try {
            LocalDateTime sentAt = LocalDateTime.of(2026, 11, 20, 12, 0);
            worker.executeWithoutResult(first -> {
                UserKeywordSubscription subscription = match("first " + keyword, userId);
                // The first worker has only read the page: no update has acquired a write lock yet.
                assertThatThrownBy(() -> worker.executeWithoutResult(second -> {
                    entityManager.createNativeQuery("SET LOCAL lock_timeout = '500ms'").executeUpdate();
                    match("second " + keyword, userId);
                })).rootCause().isInstanceOfSatisfying(SQLException.class,
                        error -> assertThat(error.getSQLState()).isEqualTo("55P03"));
                subscription.markNotified(sentAt);
            });
            worker.executeWithoutResult(second ->
                    assertThat(match("second " + keyword, userId).getLastNotifiedAt()).isEqualTo(sentAt));
        } finally {
            worker.executeWithoutResult(status -> {
                subscriptions.findByUser_UserIdAndKeyword(userId, keyword).ifPresent(subscriptions::delete);
                subscriptions.flush();
                entityManager.remove(entityManager.find(User.class, userId));
            });
        }
    }

    @Test
    void staleFanoutPageCannotLockAfterAnotherWorkerAdvancesRetriesOrCompletes() {
        LocalDateTime due = LocalDateTime.of(2001, 1, 1, 0, 0);
        Long id = createFanout(due).jobId();
        KeywordFanoutPage first = duePage(id, due);

        fanouts.findDuePageForUpdate(id, first.lastSubscriptionId(), first.retryCount(), due)
                .orElseThrow().advance(100L, false, due);
        entityManager.flush();
        entityManager.clear();
        assertThat(fanouts.findDuePageForUpdate(id, first.lastSubscriptionId(), first.retryCount(), due)).isEmpty();

        LocalDateTime retryAt = due.plusMinutes(1);
        fanouts.findDuePageForUpdate(id, 100L, 0, due).orElseThrow().fail("Original failure", retryAt);
        entityManager.flush();
        entityManager.clear();
        assertThat(fanouts.findDuePageForUpdate(id, 100L, 0, retryAt)).isEmpty();
        assertThat(fanouts.findDuePageForUpdate(id, 100L, 1, due)).isEmpty();

        fanouts.findDuePageForUpdate(id, 100L, 1, retryAt).orElseThrow().advance(100L, true, retryAt);
        entityManager.flush();
        entityManager.clear();
        assertThat(fanouts.findDuePageForUpdate(id, 100L, 1, retryAt)).isEmpty();
        assertThat(fanouts.findById(id).orElseThrow().getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.COMPLETED);
    }

    @Test
    void staleFailureWaitsForTheSuccessfulWorkerAndCannotOverwriteItsCommittedPage() throws Exception {
        LocalDateTime due = LocalDateTime.of(2001, 1, 1, 0, 0);
        TransactionTemplate worker = new TransactionTemplate(transactionManager);
        worker.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        FanoutFixture fixture = worker.execute(tx -> createFanout(due));

        try (var executor = Executors.newSingleThreadExecutor()) {
            var lateFailure = worker.execute(tx -> {
                KeywordFanoutPage original = duePage(fixture.jobId(), due);
                KeywordNotificationFanoutJob job = fanouts.findDuePageForUpdate(
                        original.jobId(), original.lastSubscriptionId(), original.retryCount(), due).orElseThrow();
                job.advance(100L, false, due);
                entityManager.flush();

                int winnerPid = jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class);
                var waiterPid = new CompletableFuture<Integer>();
                var failed = executor.submit(() -> worker.execute(other -> {
                    jdbc.execute("SET LOCAL lock_timeout = '10s'");
                    KeywordFanoutPage snapshot = duePage(fixture.jobId(), due);
                    assertThat(snapshot).isEqualTo(original);
                    waiterPid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    var current = fanouts.findDuePageForUpdate(
                            snapshot.jobId(), snapshot.lastSubscriptionId(), snapshot.retryCount(), due);
                    current.ifPresent(candidate -> candidate.fail("late failure", due.plusMinutes(1)));
                    return current.isPresent();
                }));
                await().pollInSameThread().atMost(5, TimeUnit.SECONDS).until(() -> waiterPid.isDone()
                        && Boolean.TRUE.equals(jdbc.queryForObject(
                                "SELECT ? = ANY(pg_blocking_pids(?))", Boolean.class, winnerPid, waiterPid.join())));
                return failed;
            });

            assertThat(lateFailure.get(10, TimeUnit.SECONDS)).isFalse();
            worker.executeWithoutResult(tx -> {
                KeywordNotificationFanoutJob current = fanouts.findById(fixture.jobId()).orElseThrow();
                assertThat(current.getLastSubscriptionId()).isEqualTo(100L);
                assertThat(current.getStatus()).isEqualTo(KeywordNotificationFanoutJob.Status.PENDING);
                assertThat(current.getRetryCount()).isZero();
                assertThat(current.getLastError()).isNull();
            });
        } finally {
            worker.executeWithoutResult(tx -> {
                jdbc.update("DELETE FROM keyword_notification_fanout_jobs WHERE fanout_job_id = ?", fixture.jobId());
                jdbc.update("DELETE FROM posts WHERE post_id = ?", fixture.postId());
                jdbc.update("DELETE FROM boards WHERE board_id = ?", fixture.boardId());
                jdbc.update("DELETE FROM users WHERE user_id = ?", fixture.userId());
            });
        }
    }

    private record FanoutFixture(Long jobId, Long postId, Long boardId, Long userId) {
    }

    private FanoutFixture createFanout(LocalDateTime due) {
        String name = "fanout-" + UUID.randomUUID().toString().substring(0, 8);
        User author = User.builder().loginId(name).displayName(name).email(name + "@example.org").password("test").build();
        entityManager.persist(author);
        Board board = Board.builder().creator(author).boardName(name).boardUrl(name).build();
        entityManager.persist(board);
        Post post = Post.builder().board(board).user(author).title("Title").contents("Body").build();
        entityManager.persist(post);
        KeywordNotificationFanoutJob job = fanouts.saveAndFlush(KeywordNotificationFanoutJob.pending(post.getPostId(), due));
        return new FanoutFixture(job.getFanoutJobId(), post.getPostId(), board.getBoardId(), author.getUserId());
    }

    private KeywordFanoutPage duePage(Long jobId, LocalDateTime due) {
        return fanouts.findDuePages(due, PageRequest.of(0, 100)).stream()
                .filter(page -> page.jobId().equals(jobId)).findFirst().orElseThrow();
    }

    private UserKeywordSubscription match(String title, Long userId) {
        return subscriptions.findMatchingTitleAfter(title, 0L, PageRequest.of(0, 100)).stream()
                .filter(subscription -> subscription.getUser().getUserId().equals(userId))
                .findFirst().orElseThrow();
    }
}
