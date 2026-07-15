package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.constant.BoardPolicyConstants;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.ViewHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class ViewHistoryRepositoryTest {

    private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ViewHistoryRepository viewHistoryRepository;

    private User viewer;
    private User author;
    private Board board;

    @BeforeEach
    void setUp() {
        viewer = User.builder()
                .loginId("view-history-viewer")
                .email("viewer@test.com")
                .password("password")
                .displayName("viewer")
                .build();
        author = User.builder()
                .loginId("view-history-author")
                .email("author@test.com")
                .password("password")
                .displayName("author")
                .build();
        entityManager.persist(viewer);
        entityManager.persist(author);

        board = Board.builder()
                .boardName("view-history-board")
                .boardUrl("view-history-board")
                .creator(author)
                .build();
        entityManager.persist(board);
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("같은 조회 수정 시각의 최근 본 글은 postId 내림차순으로 조회된다")
    void findVisiblePostIdsByUserIdOrderByModifiedAtDesc_ordersByModifiedAtAndPostIdDesc() {
        User managedViewer = entityManager.find(User.class, viewer.getUserId());
        User managedAuthor = entityManager.find(User.class, author.getUserId());
        Board managedBoard = entityManager.find(Board.class, board.getBoardId());
        Post olderPost = persistPost(managedBoard, managedAuthor, "older-post-id");
        Post newerPost = persistPost(managedBoard, managedAuthor, "newer-post-id");
        entityManager.persist(ViewHistory.builder()
                .user(managedViewer)
                .post(olderPost)
                .build());
        entityManager.persist(ViewHistory.builder()
                .user(managedViewer)
                .post(newerPost)
                .build());
        entityManager.flush();

        LocalDateTime sameModifiedAt = LocalDateTime.of(2030, 1, 1, 0, 0);
        updateViewHistoryModifiedAt(managedViewer.getUserId(), olderPost.getPostId(), sameModifiedAt);
        updateViewHistoryModifiedAt(managedViewer.getUserId(), newerPost.getPostId(), sameModifiedAt);
        entityManager.clear();

        Page<Long> result = viewHistoryRepository.findVisiblePostIdsByUserIdOrderByModifiedAtDesc(
                viewer.getUserId(),
                false,
                true,
                NO_BLOCKED_USER_IDS,
                BoardPolicyConstants.INQUIRY_BOARD_URL,
                PageRequest.of(0, 10, Sort.by(
                        Sort.Order.desc("modified_at"),
                        Sort.Order.desc("post_id"))));

        assertThat(result.getContent()).containsExactly(newerPost.getPostId(), olderPost.getPostId());
    }

    private Post persistPost(Board board, User author, String title) {
        Post post = Post.builder()
                .board(board)
                .user(author)
                .title(title)
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(post);
        return post;
    }

    private void updateViewHistoryModifiedAt(Long userId, Long postId, LocalDateTime modifiedAt) {
        entityManager.getEntityManager()
                .createNativeQuery("""
                        UPDATE view_histories
                        SET modified_at = :modifiedAt
                        WHERE user_id = :userId
                          AND post_id = :postId
                        """)
                .setParameter("modifiedAt", modifiedAt)
                .setParameter("userId", userId)
                .setParameter("postId", postId)
                .executeUpdate();
    }
}
