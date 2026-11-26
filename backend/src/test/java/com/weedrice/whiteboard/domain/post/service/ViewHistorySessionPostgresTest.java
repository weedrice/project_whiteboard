package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.inquiry.legacy.InquiryLegacyWritePolicy;
import com.weedrice.whiteboard.domain.post.dto.ViewHistoryRequest;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.post.integration.PostCommentStatusIntegrationAdapter;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.awaitility.Awaitility.await;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, PostViewHistoryService.class, ViewHistoryCommandService.class,
        PostCommentStatusIntegrationAdapter.class})
class ViewHistorySessionPostgresTest {
    @Autowired private EntityManager entityManager;
    @Autowired private PostViewHistoryService service;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private TransactionTemplate transactions;
    @MockitoBean private PostSummaryAssembler summaries;
    @MockitoBean private InquiryLegacyWritePolicy inquiryPolicy;

    private User viewer;
    private Board board;
    private Post post;

    @BeforeEach
    void createPost() {
        transactions.executeWithoutResult(status -> {
            String key = "vh-" + UUID.randomUUID().toString().substring(0, 10);
            viewer = User.builder().loginId(key).email(key + "@example.org").displayName(key).password("test").build();
            entityManager.persist(viewer);
            board = Board.builder().boardName(key).boardUrl(key).creator(viewer).build();
            entityManager.persist(board);
            post = Post.builder().user(viewer).board(board).title("View history").contents("Post").build();
            entityManager.persist(post);
        });
    }

    @AfterEach
    void cleanup() {
        transactions.executeWithoutResult(status -> {
            jdbc.update("DELETE FROM view_histories WHERE post_id = ?", post.getPostId());
            jdbc.update("DELETE FROM comments WHERE post_id = ?", post.getPostId());
            entityManager.remove(entityManager.find(Post.class, post.getPostId()));
            entityManager.flush();
            entityManager.remove(entityManager.find(Board.class, board.getBoardId()));
            entityManager.flush();
            entityManager.remove(entityManager.find(User.class, viewer.getUserId()));
        });
    }

    @Test
    void overlappingRequestsAndIndependentTabsCountEachSessionOnlyOnce() throws Exception {
        UUID session = UUID.randomUUID();
        service.update(viewer, post, new ViewHistoryRequest(null, 3L));
        try (var executor = Executors.newSingleThreadExecutor()) {
            var retry = transactions.execute(status -> {
                report(session, 30_000, 30_000);
                var result = executor.submit(() -> report(session, 35_000, 35_000));
                await().pollInSameThread().atMost(10, TimeUnit.SECONDS).until(() ->
                        Boolean.TRUE.equals(jdbc.queryForObject("""
                                SELECT EXISTS (SELECT FROM pg_locks
                                    WHERE NOT granted AND pg_backend_pid() = ANY(pg_blocking_pids(pid)))
                                """, Boolean.class)));
                return result;
            });
            retry.get(10, TimeUnit.SECONDS);
        }
        report(session, 30_000, 30_000);
        report(session, 35_000, 35_000);
        report(UUID.randomUUID(), 5_000, 5_000);
        assertThat(duration()).isEqualTo(40_003);
        Long historyId = jdbc.queryForObject("SELECT view_history_id FROM view_histories WHERE post_id = ?",
                Long.class, post.getPostId());
        assertThat(sessionCount(historyId)).isEqualTo(2);
        jdbc.update("DELETE FROM view_histories WHERE view_history_id = ?", historyId);
        assertThat(sessionCount(historyId)).isZero();
    }

    @Test
    void rolloutAndLongVisitsRespectTheDeltaLimitWithoutReplayingTheWatermark() {
        UUID session = UUID.randomUUID();
        long day = ViewHistoryRequest.MAX_DURATION_MS;
        // Older server recorded a day before the client started reaching a session-aware server.
        service.update(viewer, post, new ViewHistoryRequest(null, day));
        report(session, 7, day + 7);
        report(session, 7, day + 7);
        report(session, day, 2 * day + 7);
        assertThat(duration()).isEqualTo(2 * day + 7);
        assertThatThrownBy(() -> report(session, day + 1, 3 * day + 8)).isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(viewer, post, new ViewHistoryRequest(null, 1L, session, null)))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> service.update(viewer, post, new ViewHistoryRequest(null, null, session, 20L)))
                .isInstanceOf(BusinessException.class);
        assertThat(duration()).isEqualTo(2 * day + 7);
    }

    @Test
    void delayedTabCanReportItsDeletedOlderCursorWithoutReversingProgressOrCountingRetriesTwice() {
        var ids = transactions.execute(status -> {
            Comment older = Comment.builder().post(post).user(viewer).depth(0).content("Earlier").build();
            Comment later = Comment.builder().post(post).user(viewer).depth(0).content("Later").build();
            entityManager.persist(older);
            entityManager.persist(later);
            return java.util.List.of(older.getCommentId(), later.getCommentId());
        });
        UUID firstTab = UUID.randomUUID();
        service.update(viewer, post, new ViewHistoryRequest(ids.get(0), 10L, firstTab, 10L));
        service.update(viewer, post, new ViewHistoryRequest(ids.get(1), 20L, UUID.randomUUID(), 20L));
        transactions.executeWithoutResult(status -> entityManager.find(Comment.class, ids.get(0)).deleteComment());

        var delayed = new ViewHistoryRequest(ids.get(0), 5L, firstTab, 15L);
        service.update(viewer, post, delayed);
        service.update(viewer, post, delayed);
        service.update(viewer, post, new ViewHistoryRequest(ids.get(0), 3L));

        assertThat(duration()).isEqualTo(38);
        assertThat(jdbc.queryForObject("SELECT last_read_comment_id FROM view_histories WHERE post_id = ?",
                Long.class, post.getPostId())).isEqualTo(ids.get(1));
    }

    private void report(UUID session, long unconfirmed, long cumulative) {
        service.update(viewer, post, new ViewHistoryRequest(null, unconfirmed, session, cumulative));
    }

    private long duration() {
        return jdbc.queryForObject("SELECT duration_ms FROM view_histories WHERE post_id = ?",
                Long.class, post.getPostId());
    }

    private long sessionCount(Long historyId) {
        return jdbc.queryForObject("SELECT count(*) FROM view_history_sessions WHERE view_history_id = ?",
                Long.class, historyId);
    }
}
