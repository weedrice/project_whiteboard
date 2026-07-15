package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardVisit;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class BoardVisitRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BoardVisitRepository boardVisitRepository;

    @Test
    void findActivityByBoardIds_countsOnlyPostsAfterRecentCutoff() {
        User user = persistUser("reader");
        User writer = persistUser("writer");
        Board board = persistBoard("activity-board", writer);
        LocalDateTime now = LocalDateTime.of(2026, 7, 9, 12, 0);
        LocalDateTime recentCutoff = now.minusDays(30);
        entityManager.persist(new BoardVisit(user, board, now.minusDays(60)));

        Post excludedOldPost = persistPost(board, writer, "old");
        Post recentPost = persistPost(board, writer, "recent");
        setCreatedAt(excludedOldPost, now.minusDays(35));
        setCreatedAt(recentPost, now.minusDays(5));
        entityManager.flush();
        entityManager.clear();

        List<BoardVisitRepository.BoardActivityProjection> result = boardVisitRepository.findActivityByBoardIds(
                List.of(board.getBoardId()),
                user.getUserId(),
                false,
                recentCutoff);

        assertThat(result).hasSize(1);
        assertThat(result.get(0).getBoardId()).isEqualTo(board.getBoardId());
        assertThat(result.get(0).getNewPostCount()).isEqualTo(1L);
        assertThat(result.get(0).getLatestPostAt()).isEqualTo(now.minusDays(5));
    }

    private User persistUser(String loginId) {
        User user = User.builder()
                .loginId(loginId)
                .email(loginId + "@test.com")
                .password("password")
                .displayName(loginId)
                .build();
        entityManager.persist(user);
        return user;
    }

    private Board persistBoard(String boardUrl, User creator) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(true)
                .build();
        entityManager.persist(board);
        return board;
    }

    private Post persistPost(Board board, User writer, String title) {
        Post post = Post.builder()
                .board(board)
                .user(writer)
                .title(title)
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(post);
        entityManager.flush();
        return post;
    }

    private void setCreatedAt(Post post, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE posts SET created_at = :createdAt, modified_at = :createdAt WHERE post_id = :postId")
                .setParameter("createdAt", createdAt)
                .setParameter("postId", post.getPostId())
                .executeUpdate();
    }
}
