package com.weedrice.whiteboard.domain.comment.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.entity.CommentLike;
import com.weedrice.whiteboard.domain.comment.entity.CommentLikeId;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class CommentLikeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private CommentLikeRepository commentLikeRepository;

    private User likedUser;
    private Comment comment;

    @BeforeEach
    void setUp() {
        User author = User.builder()
                .loginId("comment-author")
                .email("comment-author@test.com")
                .password("password")
                .displayName("comment author")
                .build();
        likedUser = User.builder()
                .loginId("comment-liker")
                .email("comment-liker@test.com")
                .password("password")
                .displayName("comment liker")
                .build();
        entityManager.persist(author);
        entityManager.persist(likedUser);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(author)
                .build();
        entityManager.persist(board);

        Post post = Post.builder()
                .board(board)
                .user(author)
                .title("commented post")
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(post);

        comment = Comment.builder()
                .post(post)
                .user(author)
                .content("liked comment")
                .depth(0)
                .build();
        entityManager.persist(comment);

        entityManager.persist(CommentLike.builder()
                .user(likedUser)
                .comment(comment)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("deleteByUserIdAndCommentId deletes the matching like row")
    void deleteByUserIdAndCommentId_success() {
        int deletedCount = commentLikeRepository.deleteByUserIdAndCommentId(
                likedUser.getUserId(),
                comment.getCommentId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedCount).isEqualTo(1);
        assertThat(commentLikeRepository.existsById(new CommentLikeId(
                likedUser.getUserId(),
                comment.getCommentId()))).isFalse();
    }
}
