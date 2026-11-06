package com.weedrice.whiteboard.domain.actor.query;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardVisitRepository;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.query.CommentVisibleCountReader;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.post.repository.ViewHistoryRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jdbc.test.autoconfigure.AutoConfigureTestDatabase;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.context.ActiveProfiles;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest(showSql = false, properties = "spring.flyway.postgresql.transactional-lock=false")
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("postgres-smoke")
@EnabledIfEnvironmentVariable(named = "POSTGRES_SMOKE_TEST", matches = "true")
@Import({QuerydslConfig.class, ContentAuthorVisibilityRepository.class, CommentVisibleCountReader.class})
class ContentAuthorVisibilityPostgresTest {

    @Autowired private TestEntityManager entityManager;
    @Autowired private PostRepository posts;
    @Autowired private ContentAuthorVisibilityRepository authors;
    @Autowired private BoardVisitRepository boardActivity;
    @Autowired private CommentVisibleCountReader commentCounts;
    @Autowired private ViewHistoryRepository history;

    @Test
    void activityAndVisibleCommentCountFollowCurrentBanWithoutMutatingStoredCount() {
        User active = persistUser("counts-pg-active");
        User banned = persistUser("counts-pg-banned");
        Board board = entityManager.persist(Board.builder().creator(active)
                .boardName("Counts PG").boardUrl("counts-pg").build());
        Post post = entityManager.persist(Post.builder().board(board).user(active)
                .title("Visible post").contents("content").build());
        entityManager.persist(Post.builder().board(board).user(banned)
                .title("Hidden post").contents("content").build());
        Comment hiddenRoot = entityManager.persist(Comment.builder().post(post).user(banned)
                .depth(0).content("hidden root").build());
        entityManager.persist(Comment.builder().post(post).user(active).parent(hiddenRoot)
                .depth(1).content("visible reply").build());
        post.incrementCommentCount();
        post.incrementCommentCount();
        Sanction ban = entityManager.persist(Sanction.builder().targetUser(banned).processorUser(active)
                .type("BAN").startDate(LocalDateTime.now().minusDays(1)).build());
        entityManager.flush();
        entityManager.clear();

        var activity = boardActivity.findActivityByBoardIds(List.of(board.getBoardId()), active.getUserId(),
                false, LocalDateTime.now().minusDays(30));
        assertThat(activity.getFirst().getNewPostCount()).isEqualTo(1);
        assertThat(commentCounts.countByPostIds(List.of(post.getPostId()), List.of()))
                .containsEntry(post.getPostId(), 1);
        assertThat(posts.findById(post.getPostId()).orElseThrow().getCommentCount()).isEqualTo(2);

        entityManager.getEntityManager().createNativeQuery(
                        "UPDATE sanctions SET end_date = CURRENT_TIMESTAMP WHERE sanction_id = :id")
                .setParameter("id", ban.getSanctionId()).executeUpdate();
        entityManager.clear();
        assertThat(boardActivity.findActivityByBoardIds(List.of(board.getBoardId()), null, false,
                LocalDateTime.now().minusDays(30)).getFirst().getNewPostCount()).isEqualTo(2);
        assertThat(commentCounts.countByPostIds(List.of(post.getPostId()), List.of()))
                .containsEntry(post.getPostId(), 2);
        assertThat(commentCounts.countByPostIds(List.of(post.getPostId()), List.of(banned.getUserId())))
                .containsEntry(post.getPostId(), 1);
    }

    @Test
    void migratedPostgresAppliesSamePolicyToQuerydslBatchAndWindowedLatestPosts() {
        User visibleAuthor = persistUser("visibility-pg-active");
        User bannedAuthor = persistUser("visibility-pg-banned");
        Board board = entityManager.persist(Board.builder()
                .boardName("Visibility PG").boardUrl("visibility-pg").creator(visibleAuthor).build());
        Post visible = entityManager.persist(Post.builder().board(board).user(visibleAuthor)
                .title("visibility-pg needle active").contents("Visible content").build());
        Post hidden = entityManager.persist(Post.builder().board(board).user(bannedAuthor)
                .title("visibility-pg needle banned").contents("Hidden content").build());
        Sanction ban = entityManager.persist(Sanction.builder().targetUser(bannedAuthor)
                .processorUser(visibleAuthor).type("BAN").remark("Visibility integration")
                .startDate(LocalDateTime.now().minusDays(1)).endDate(LocalDateTime.now().plusDays(1)).build());
        entityManager.flush();
        entityManager.clear();

        var search = posts.searchPostsByKeyword("visibility-pg needle", null, null, PageRequest.of(0, 1));
        assertThat(search.getTotalElements()).isEqualTo(1);
        assertThat(search.getContent()).extracting(Post::getPostId).containsExactly(visible.getPostId());
        assertThat(authors.findVisibleUserIds(List.of(visibleAuthor.getUserId(), bannedAuthor.getUserId())))
                .containsExactly(visibleAuthor.getUserId());
        assertThat(posts.findLatestPostIdsByBoardIds(List.of(board.getBoardId()), 1, List.of(), List.of(), null))
                .containsExactly(visible.getPostId());

        // Expiring the sanction changes all read adapters without rewriting content.
        entityManager.getEntityManager().createNativeQuery(
                "UPDATE sanctions SET end_date = CURRENT_TIMESTAMP WHERE sanction_id = :id")
                .setParameter("id", ban.getSanctionId()).executeUpdate();
        entityManager.clear();
        assertThat(posts.searchPostsByKeyword("visibility-pg needle", null, null, PageRequest.of(0, 1))
                .getTotalElements()).isEqualTo(2);
        assertThat(authors.findVisibleUserIds(List.of(bannedAuthor.getUserId()))).containsExactly(bannedAuthor.getUserId());
        assertThat(posts.findLatestPostIdsByBoardIds(List.of(board.getBoardId()), 1, List.of(), List.of(), null))
                .containsExactly(hidden.getPostId());
    }

    @Test
    void recentlyViewedPostsFilterAuthorsBeforePaginationAndCountAndRestoreExpiredBan() {
        User viewer = persistUser("history-pg-viewer");
        User banned = persistUser("history-pg-banned");
        User inactive = persistUser("history-pg-inactive");
        User deleted = persistUser("history-pg-deleted");
        Board board = entityManager.persist(Board.builder().creator(viewer)
                .boardName("History PG").boardUrl("history-pg").build());
        Post first = entityManager.persist(Post.builder().board(board).user(viewer)
                .title("First visible history").contents("body").build());
        Post second = entityManager.persist(Post.builder().board(board).user(viewer)
                .title("Second visible history").contents("body").build());
        Post bannedPost = entityManager.persist(Post.builder().board(board).user(banned)
                .title("Banned history").contents("body").build());
        Post inactivePost = entityManager.persist(Post.builder().board(board).user(inactive)
                .title("Inactive history").contents("body").build());
        Post deletedAuthorPost = entityManager.persist(Post.builder().board(board).user(deleted)
                .title("Deleted author history").contents("body").build());
        for (Post post : List.of(first, second, bannedPost, inactivePost, deletedAuthorPost)) {
            entityManager.persist(ViewHistory.builder().user(viewer).post(post).build());
        }
        Sanction ban = entityManager.persist(Sanction.builder().targetUser(banned).processorUser(viewer)
                .type("ban").startDate(LocalDateTime.now().minusDays(1)).build());
        entityManager.flush();
        entityManager.getEntityManager().createNativeQuery("UPDATE users SET status = 'INACTIVE' WHERE user_id = :id")
                .setParameter("id", inactive.getUserId()).executeUpdate();
        entityManager.getEntityManager().createNativeQuery("UPDATE users SET deleted_at = CURRENT_TIMESTAMP WHERE user_id = :id")
                .setParameter("id", deleted.getUserId()).executeUpdate();
        entityManager.getEntityManager().createNativeQuery("UPDATE view_histories SET modified_at = CURRENT_TIMESTAMP WHERE user_id = :id")
                .setParameter("id", viewer.getUserId()).executeUpdate();
        entityManager.clear();

        var page = history.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                viewer.getUserId(), false, true, List.of(-1L), "inquiry", false, PageRequest.of(0, 1));
        assertThat(page.getContent()).containsExactly(second.getPostId());
        assertThat(page.getTotalElements()).isEqualTo(2);
        var next = history.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                viewer.getUserId(), false, true, List.of(-1L), "inquiry", false, PageRequest.of(1, 1));
        assertThat(next.getContent()).containsExactly(first.getPostId());
        assertThat(next.getTotalElements()).isEqualTo(2);
        assertThat(history.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                viewer.getUserId(), true, true, List.of(-1L), "inquiry", false, PageRequest.of(0, 1))
                .getTotalElements()).isEqualTo(2);

        entityManager.getEntityManager().createNativeQuery(
                "UPDATE sanctions SET end_date = CURRENT_TIMESTAMP WHERE sanction_id = :id")
                .setParameter("id", ban.getSanctionId()).executeUpdate();
        entityManager.clear();
        var restored = history.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                viewer.getUserId(), false, true, List.of(-1L), "inquiry", false, PageRequest.of(0, 1));
        assertThat(restored.getContent()).containsExactly(bannedPost.getPostId());
        assertThat(restored.getTotalElements()).isEqualTo(3);
        assertThat(history.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                viewer.getUserId(), false, false, List.of(banned.getUserId()), "inquiry", false, PageRequest.of(0, 1))
                .getTotalElements()).isEqualTo(2);
        assertThat(entityManager.getEntityManager().createQuery(
                "SELECT COUNT(vh) FROM ViewHistory vh WHERE vh.userId = :id", Long.class)
                .setParameter("id", viewer.getUserId()).getSingleResult()).isEqualTo(5L);
    }

    private User persistUser(String loginId) {
        return entityManager.persist(User.builder().loginId(loginId).email(loginId + "@test.example")
                .password("test-password").displayName(loginId).build());
    }
}
