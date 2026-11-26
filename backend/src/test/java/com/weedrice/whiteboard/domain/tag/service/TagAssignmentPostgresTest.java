package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
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
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.List;
import java.util.UUID;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, TagAssignmentService.class})
class TagAssignmentPostgresTest {
    @Autowired private EntityManager entityManager;
    @Autowired private TagAssignmentService assignments;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;

    @Test
    void oppositeReplacementsBothCommitAndWaitForTheLowerNameFirst() throws Exception {
        String key = "t-" + UUID.randomUUID().toString().substring(0, 12);
        String lower = key + "-a";
        String higher = key + "-z";
        Fixture fixture = transactions.execute(status -> {
            User user = User.builder().loginId(key).displayName(key).email(key + "@example.org")
                    .password("test-password").build();
            entityManager.persist(user);
            Board board = Board.builder().boardName(key).boardUrl(key).creator(user).build();
            entityManager.persist(board);
            long first = createPost(board, user, lower);
            long second = createPost(board, user, higher);
            return new Fixture(user.getUserId(), board.getBoardId(), first, second);
        });

        try (var pool = Executors.newFixedThreadPool(2)) {
            var workers = transactions.execute(status -> {
                lockTag(lower);
                var firstPid = new CompletableFuture<Integer>();
                var first = replace(pool, fixture.first(), higher, firstPid);
                awaitBlocked(firstPid.orTimeout(5, TimeUnit.SECONDS).join());

                // A writer replacing lower with higher must wait before acquiring higher.
                // If it first locks its added tag, this NOWAIT fails immediately.
                lockTag(higher);
                var secondPid = new CompletableFuture<Integer>();
                var second = replace(pool, fixture.second(), lower, secondPid);
                awaitBlocked(secondPid.orTimeout(5, TimeUnit.SECONDS).join());
                return List.of(first, second);
            });
            for (var worker : workers) {
                worker.get(10, TimeUnit.SECONDS);
            }
            assertThat(namesForPost(fixture.first())).containsExactly(higher);
            assertThat(namesForPost(fixture.second())).containsExactly(lower);
            assertThat(jdbc.queryForList("SELECT post_count FROM tags WHERE tag_name IN (?, ?)",
                    Integer.class, lower, higher)).containsExactly(1, 1);
        } finally {
            transactions.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM post_tags WHERE post_id IN (?, ?)", fixture.first(), fixture.second());
                jdbc.update("DELETE FROM tags WHERE tag_name IN (?, ?)", lower, higher);
                jdbc.update("DELETE FROM posts WHERE post_id IN (?, ?)", fixture.first(), fixture.second());
                jdbc.update("DELETE FROM boards WHERE board_id = ?", fixture.board());
                jdbc.update("DELETE FROM users WHERE user_id = ?", fixture.user());
            });
        }
    }

    private long createPost(Board board, User user, String tag) {
        Post post = Post.builder().board(board).user(user).title(tag).contents("content").build();
        entityManager.persist(post);
        assignments.assignTags(post, List.of(tag));
        return post.getPostId();
    }

    private void lockTag(String name) {
        jdbc.queryForObject("SELECT tag_id FROM tags WHERE tag_name = ? FOR UPDATE NOWAIT", Long.class, name);
    }

    private Future<?> replace(ExecutorService pool, long postId, String name, CompletableFuture<Integer> pid) {
        return pool.submit(() -> {
            try {
                transactions.executeWithoutResult(status -> {
                    jdbc.execute("SET LOCAL statement_timeout = '15s'");
                    pid.complete(jdbc.queryForObject("SELECT pg_backend_pid()", Integer.class));
                    assignments.assignTags(entityManager.find(Post.class, postId), List.of(name));
                });
            } catch (RuntimeException e) {
                pid.completeExceptionally(e);
                throw e;
            }
        });
    }

    private void awaitBlocked(int pid) {
        await().pollInSameThread().atMost(5, TimeUnit.SECONDS).until(() -> Boolean.TRUE.equals(
                jdbc.queryForObject("SELECT cardinality(pg_blocking_pids(?)) > 0", Boolean.class, pid)));
    }

    private List<String> namesForPost(long postId) {
        return jdbc.queryForList("""
                SELECT t.tag_name FROM tags t JOIN post_tags pt ON pt.tag_id = t.tag_id
                WHERE pt.post_id = ?
                """, String.class, postId);
    }

    private record Fixture(long user, long board, long first, long second) {}
}
