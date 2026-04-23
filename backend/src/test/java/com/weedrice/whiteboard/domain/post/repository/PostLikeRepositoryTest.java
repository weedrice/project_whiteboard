package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.entity.PostLike;
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
class PostLikeRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PostLikeRepository postLikeRepository;

    private User likedUser;
    private Post post;

    @BeforeEach
    void setUp() {
        User author = User.builder()
                .loginId("author")
                .email("author@test.com")
                .password("password")
                .displayName("author")
                .build();
        likedUser = User.builder()
                .loginId("liker")
                .email("liker@test.com")
                .password("password")
                .displayName("liker")
                .build();
        entityManager.persist(author);
        entityManager.persist(likedUser);

        Board board = Board.builder()
                .boardName("free")
                .boardUrl("free")
                .creator(author)
                .build();
        entityManager.persist(board);

        post = Post.builder()
                .board(board)
                .user(author)
                .title("liked post")
                .contents("contents")
                .isNotice(false)
                .isNsfw(false)
                .isSpoiler(false)
                .isSecret(false)
                .build();
        entityManager.persist(post);

        entityManager.persist(PostLike.builder()
                .user(likedUser)
                .post(post)
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("deleteByUserIdAndPostId deletes the matching like row")
    void deleteByUserIdAndPostId_success() {
        int deletedCount = postLikeRepository.deleteByUserIdAndPostId(likedUser.getUserId(), post.getPostId());
        entityManager.flush();
        entityManager.clear();

        assertThat(deletedCount).isEqualTo(1);
        assertThat(postLikeRepository.existsById(new com.weedrice.whiteboard.domain.post.entity.PostLikeId(
                likedUser.getUserId(),
                post.getPostId()))).isFalse();
    }
}
