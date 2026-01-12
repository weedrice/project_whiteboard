package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardCategory;
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
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PostRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PostRepository postRepository;

    private User user;
    private Board board;
    private BoardCategory category;
    private Post post;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@test.com")
                .password("password")
                .displayName("Test User")
                .build();
        entityManager.persist(user);

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(user)
                .build();
        entityManager.persist(board);

        category = BoardCategory.builder()
                .name("General")
                .board(board)
                .build();
        entityManager.persist(category);

        post = Post.builder()
                .title("Test Post")
                .contents("Test Contents")
                .user(user)
                .board(board)
                .category(category)
                .build();
        entityManager.persist(post);
        entityManager.flush();
    }

    @Test
    @DisplayName("게시글 ID로 조회 성공")
    void findById_success() {
        // when
        Optional<Post> found = postRepository.findById(post.getPostId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getTitle()).isEqualTo("Test Post");
    }

    @Test
    @DisplayName("사용자별 게시글 목록 조회 성공")
    void findByUser_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Post> posts = postRepository.findByUserAndIsDeleted(user, false, pageRequest);

        // then
        assertThat(posts.getContent()).isNotEmpty();
        assertThat(posts.getContent().get(0).getUser()).isEqualTo(user);
    }
}
