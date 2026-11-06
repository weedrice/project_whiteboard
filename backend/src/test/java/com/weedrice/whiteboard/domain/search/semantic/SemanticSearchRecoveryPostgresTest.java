package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.context.ActiveProfiles;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;

/** Uses migrated PostgreSQL/pgvector and rolls back each test; never calls the embedding provider. */
@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Import(QuerydslConfig.class)
class SemanticSearchRecoveryPostgresTest {
    @Autowired private TestEntityManager entityManager;
    @Autowired private JdbcTemplate jdbc;
    @Autowired private PostRepository posts;
    @Autowired private CommentRepository comments;
    @Autowired private BoardRepository boards;
    @Autowired private UserRepository users;

    private SemanticSearchJobRepository jobs;
    private SemanticSearchEmbeddingRepository embeddings;
    private SemanticSearchIndexTransactionService indexing;
    private SemanticSearchBackfillService backfill;
    private Post post;
    private Comment comment;

    @BeforeEach
    void createFixtures() {
        Clock clock = Clock.systemUTC();
        jobs = new SemanticSearchJobRepository(jdbc, clock);
        embeddings = new SemanticSearchEmbeddingRepository(jdbc, clock);
        indexing = new SemanticSearchIndexTransactionService(posts, comments, boards, users,
                new SemanticSearchTextBuilder(), embeddings);
        backfill = new SemanticSearchBackfillService(jdbc, jobs);
        User user = entityManager.persist(User.builder().loginId("semantic-recovery-pg")
                .email("semantic-recovery-pg@test.example").password("test-password")
                .displayName("semantic-recovery-pg").build());
        Board board = entityManager.persist(Board.builder().boardName("Recovery PG")
                .boardUrl("semantic-recovery-pg").creator(user).build());
        post = entityManager.persist(Post.builder().board(board).user(user)
                .title("Recovery title").contents("Latest body").build());
        comment = entityManager.persist(Comment.builder().post(post).user(user).depth(0).content("Latest comment").build());
        entityManager.flush();
        float[] vector = new float[1536];
        vector[0] = 1;
        embeddings.upsertPost(post.getPostId(), board.getBoardId(), user.getUserId(), null,
                "old body", "old-hash", "text-embedding-3-small", vector);
        embeddings.upsertComment(comment.getCommentId(), post.getPostId(), board.getBoardId(), user.getUserId(), null,
                "old comment", "old-hash", "text-embedding-3-small", vector);
        entityManager.clear();
    }

    @Test
    void backfillRepairsFailedUpdatesWithExistingLiveVectorsAndDoesNotDuplicatePendingJobs() {
        jobs.enqueue("POST", post.getPostId(), SemanticSearchIndexAction.UPSERT);
        jobs.enqueue("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.UPSERT);
        jdbc.update("UPDATE semantic_search_jobs SET status = 'FAILED', retry_count = 5 WHERE content_type = 'POST' AND content_id = ?",
                post.getPostId());
        jdbc.update("UPDATE semantic_search_jobs SET status = 'FAILED', retry_count = 5 WHERE content_type = 'COMMENT' AND content_id = ?",
                comment.getCommentId());

        backfill.enqueueBackfill("ALL", 1000);
        backfill.enqueueBackfill("ALL", 1000);

        assertThat(status("POST", post.getPostId())).isEqualTo("PENDING");
        assertThat(status("COMMENT", comment.getCommentId())).isEqualTo("PENDING");
        assertThat(jdbc.queryForObject("SELECT retry_count FROM semantic_search_jobs WHERE content_type = 'POST' AND content_id = ?",
                Integer.class, post.getPostId())).isZero();
        assertThat(jdbc.queryForObject("SELECT COUNT(*) FROM semantic_search_jobs WHERE content_type = 'POST' AND content_id = ?",
                Long.class, post.getPostId())).isEqualTo(1);
        assertThat(isLive("POST", post.getPostId())).isTrue();
    }

    @Test
    void backfillRedrivesFailedDeletionOfNowHiddenContentWithinLimit() {
        jobs.enqueue("POST", post.getPostId(), SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE posts SET is_deleted = 'Y' WHERE post_id = ?", post.getPostId());
        jdbc.update("UPDATE semantic_search_jobs SET status = 'FAILED', retry_count = 5 WHERE content_type = 'POST' AND content_id = ?",
                post.getPostId());

        assertThat(backfill.enqueueBackfill("POST", 1)).isEqualTo(1);

        assertThat(status("POST", post.getPostId())).isEqualTo("PENDING");
        var deletion = jobs.findPendingDeletionJobs(5, 1000).stream()
                .filter(job -> job.contentType().equals("POST") && job.contentId().equals(post.getPostId()))
                .findFirst().orElseThrow();
        assertThat(deletion.action()).isEqualTo("DELETE");
        indexing.delete(deletion.contentType(), deletion.contentId());
        assertThat(isLive("POST", post.getPostId())).isFalse();
        assertThat(isLive("COMMENT", comment.getCommentId())).isFalse();
        assertThat(jobs.redriveFailedDeletes("POST", 1)).isZero();
    }

    @Test
    void offlineDeletionSelectionAndLeaseReleasePreserveNewerUpsert() {
        jobs.enqueue("POST", post.getPostId(), SemanticSearchIndexAction.DELETE);
        jobs.enqueue("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.UPSERT);
        var deletion = jobs.findPendingDeletionJobs(5, 1000).stream()
                .filter(job -> job.contentType().equals("POST") && job.contentId().equals(post.getPostId()))
                .findFirst().orElseThrow();
        assertThat(jobs.findPendingDeletionJobs(5, 1000)).noneMatch(job -> job.contentType().equals("COMMENT"));
        jobs.enqueue("POST", post.getPostId(), SemanticSearchIndexAction.UPSERT);
        LocalDateTime lease = LocalDateTime.now().truncatedTo(ChronoUnit.MICROS);
        assertThat(jobs.claimForProcessing(deletion.jobId(), 5, lease)).isEqualTo(1);
        assertThat(jobs.findClaimed(deletion.jobId(), lease).orElseThrow().action()).isEqualTo("UPSERT");
        assertThat(jobs.releaseClaim(deletion.jobId(), lease.minusSeconds(1))).isZero();
        assertThat(jobs.releaseClaim(deletion.jobId(), lease)).isEqualTo(1);
        assertThat(status("POST", post.getPostId())).isEqualTo("PENDING");
    }

    @Test
    void latePostDeleteRechecksRestoredStateBeforeTouchingVectors() {
        jdbc.update("UPDATE posts SET is_blinded = 'Y' WHERE post_id = ?", post.getPostId());
        // A DELETE worker was selected while hidden; restoration wins before its locking write.
        jobs.enqueue("POST", post.getPostId(), SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE posts SET is_blinded = 'N' WHERE post_id = ?", post.getPostId());
        jobs.enqueue("POST", post.getPostId(), SemanticSearchIndexAction.UPSERT);

        indexing.delete("POST", post.getPostId());

        assertThat(isLive("POST", post.getPostId())).isTrue();
        assertThat(isLive("COMMENT", comment.getCommentId())).isTrue();
        jdbc.update("UPDATE posts SET is_blinded = 'Y' WHERE post_id = ?", post.getPostId());
        entityManager.clear();
        indexing.delete("POST", post.getPostId());
        assertThat(isLive("POST", post.getPostId())).isFalse();
        assertThat(isLive("COMMENT", comment.getCommentId())).isFalse();
    }

    @Test
    void lateCommentDeleteRechecksRestoredStateBeforeTouchingVector() {
        jdbc.update("UPDATE comments SET is_blinded = 'Y' WHERE comment_id = ?", comment.getCommentId());
        jobs.enqueue("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.DELETE);
        jdbc.update("UPDATE comments SET is_blinded = 'N' WHERE comment_id = ?", comment.getCommentId());
        jobs.enqueue("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.UPSERT);

        indexing.delete("COMMENT", comment.getCommentId());

        assertThat(isLive("COMMENT", comment.getCommentId())).isTrue();
        jdbc.update("UPDATE comments SET is_blinded = 'Y' WHERE comment_id = ?", comment.getCommentId());
        entityManager.clear();
        indexing.delete("COMMENT", comment.getCommentId());
        assertThat(isLive("COMMENT", comment.getCommentId())).isFalse();
        assertThat(isLive("POST", post.getPostId())).isTrue();
    }

    @Test
    void withdrawnAuthorContentRemainsIndexedAndBackfillRepairsPreviousTombstones() {
        jdbc.update("UPDATE users SET status = 'DELETED', deleted_at = CURRENT_TIMESTAMP WHERE user_id = ?",
                post.getUserId());

        indexing.delete("POST", post.getPostId());
        indexing.delete("COMMENT", comment.getCommentId());
        assertThat(isLive("POST", post.getPostId())).isTrue();
        assertThat(isLive("COMMENT", comment.getCommentId())).isTrue();

        // Repair vectors tombstoned by the former account-active-only policy.
        embeddings.tombstone("POST", post.getPostId());
        embeddings.tombstone("COMMENT", comment.getCommentId());
        backfill.enqueueBackfill("ALL", 1000);
        assertThat(status("POST", post.getPostId())).isEqualTo("PENDING");
        assertThat(status("COMMENT", comment.getCommentId())).isEqualTo("PENDING");

        var postPayload = indexing.loadPostIndexPayload(post.getPostId());
        var commentPayload = indexing.loadCommentIndexPayload(comment.getCommentId());
        assertThat(postPayload).isNotNull();
        assertThat(commentPayload).isNotNull();
        float[] vector = new float[1536];
        vector[0] = 1;
        indexing.upsertPost(postPayload, "text-embedding-3-small", vector);
        indexing.upsertComment(commentPayload, "text-embedding-3-small", vector);
        assertThat(isLive("POST", post.getPostId())).isTrue();
        assertThat(isLive("COMMENT", comment.getCommentId())).isTrue();
    }

    @Test
    void suspendedAuthorRemainsExcludedFromIndexingAndBackfill() {
        jdbc.update("UPDATE users SET status = 'SUSPENDED' WHERE user_id = ?", post.getUserId());
        indexing.delete("POST", post.getPostId());

        assertThat(indexing.loadPostIndexPayload(post.getPostId())).isNull();
        assertThat(indexing.loadCommentIndexPayload(comment.getCommentId())).isNull();
        assertThat(isLive("POST", post.getPostId())).isFalse();
        assertThat(isLive("COMMENT", comment.getCommentId())).isFalse();
        backfill.enqueueBackfill("ALL", 1000);
        assertThat(jdbc.queryForObject("""
                SELECT COUNT(*) FROM semantic_search_jobs
                WHERE (content_type = 'POST' AND content_id = ?)
                   OR (content_type = 'COMMENT' AND content_id = ?)
                """, Long.class, post.getPostId(), comment.getCommentId())).isZero();
    }

    private String status(String type, Long id) {
        return jdbc.queryForObject("SELECT status FROM semantic_search_jobs WHERE content_type = ? AND content_id = ?",
                String.class, type, id);
    }

    private boolean isLive(String type, Long id) {
        return Boolean.TRUE.equals(jdbc.queryForObject(
                "SELECT deleted_at IS NULL FROM semantic_search_embeddings WHERE content_type = ? AND content_id = ?",
                Boolean.class, type, id));
    }
}
