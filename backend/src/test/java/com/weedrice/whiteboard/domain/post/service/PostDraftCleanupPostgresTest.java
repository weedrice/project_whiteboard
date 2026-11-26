package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.file.service.FileService;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.post.repository.DraftPostRepository;
import com.weedrice.whiteboard.domain.post.scheduled.entity.ScheduledPost;
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
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.UUID;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.awaitility.Awaitility.await;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Transactional(propagation = Propagation.NOT_SUPPORTED)
@Import({QuerydslConfig.class, PostDraftCleanupBatchService.class})
class PostDraftCleanupPostgresTest {
    @Autowired private EntityManager entityManager;
    @Autowired private DraftPostRepository drafts;
    @Autowired private PostDraftCleanupBatchService cleanup;
    @Autowired private TransactionTemplate transactions;
    @Autowired private JdbcTemplate jdbc;
    @MockitoBean private FileService files;

    @Test
    void expiryKeepsOriginalWhenReservationCommitsWhileCleanupWaitsForDraftLock() throws Exception {
        String key = "d-" + UUID.randomUUID().toString().substring(0, 12);
        LocalDateTime cutoff = LocalDateTime.of(1960, 1, 1, 0, 0);
        record Fixture(Long user, Long board, Long draft) {}
        Fixture fixture = transactions.execute(status -> {
            User user = User.builder().loginId(key).displayName(key).email(key + "@example.org")
                    .password("test-password").build();
            entityManager.persist(user);
            Board board = Board.builder().boardName(key).boardUrl(key).creator(user).build();
            entityManager.persist(board);
            DraftPost draft = drafts.saveAndFlush(DraftPost.builder().user(user).board(board)
                    .title("Original draft").contents("Original text").build());
            jdbc.update("UPDATE draft_posts SET modified_at = ? WHERE draft_id = ?", cutoff.minusDays(1), draft.getDraftId());
            return new Fixture(user.getUserId(), board.getBoardId(), draft.getDraftId());
        });

        try (var executor = Executors.newSingleThreadExecutor()) {
            var pending = transactions.execute(status -> {
                DraftPost draft = drafts.findByDraftIdAndUserForUpdate(fixture.draft(), fixture.user()).orElseThrow();
                entityManager.persist(ScheduledPost.builder().userId(fixture.user()).board(draft.getBoard())
                        .title("Scheduled").contents("Text").draftId(fixture.draft()).scheduledAt(cutoff).build());
                entityManager.flush();

                var result = executor.submit(() -> cleanup.cleanupExpiredBatch(cutoff, 10));
                await().pollInSameThread().atMost(10, TimeUnit.SECONDS).until(() ->
                        Boolean.TRUE.equals(jdbc.queryForObject("""
                                SELECT EXISTS (SELECT FROM pg_locks
                                    WHERE NOT granted AND pg_backend_pid() = ANY(pg_blocking_pids(pid)))
                                """, Boolean.class)));
                return result;
            });
            assertThat(pending.get(10, TimeUnit.SECONDS))
                    .isEqualTo(new PostDraftCleanupBatchService.BatchResult(1, 0));
            assertThat(drafts.existsById(fixture.draft())).isTrue();
            assertThat(jdbc.queryForObject("SELECT draft_id FROM scheduled_posts WHERE user_id = ?",
                    Long.class, fixture.user())).isEqualTo(fixture.draft());
            verify(files, never()).markDraftFilesDeletionPending(fixture.draft());
        } finally {
            transactions.executeWithoutResult(status -> {
                jdbc.update("DELETE FROM scheduled_posts WHERE user_id = ?", fixture.user());
                drafts.deleteAllByIdInBatch(java.util.List.of(fixture.draft()));
                entityManager.remove(entityManager.find(Board.class, fixture.board()));
                entityManager.flush();
                entityManager.remove(entityManager.find(User.class, fixture.user()));
            });
        }
    }
}
