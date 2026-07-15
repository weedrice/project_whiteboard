package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.PopularPost;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class PopularPostRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private PopularPostRepository popularPostRepository;

    @Test
    @DisplayName("인기글 조회는 post와 board를 함께 로딩한다")
    void findByRankingTypeOrderByRankAsc_fetchesPostAndBoard() {
        User user = persistUser();
        Board board = persistBoard(user);
        Post post = persistPost(user, board);
        PopularPost popularPost = PopularPost.builder()
                .rankingType("DAILY")
                .post(post)
                .score(10.0)
                .rank(1)
                .build();
        entityManager.persist(popularPost);
        entityManager.flush();
        entityManager.clear();

        PopularPost found = popularPostRepository.findByRankingTypeOrderByRankAsc("DAILY", PageRequest.of(0, 10))
                .getContent()
                .getFirst();

        PersistenceUnitUtil persistenceUnitUtil = entityManager.getEntityManager()
                .getEntityManagerFactory()
                .getPersistenceUnitUtil();
        assertThat(found.getRank()).isEqualTo(1);
        assertThat(persistenceUnitUtil.isLoaded(found, "post")).isTrue();
        assertThat(persistenceUnitUtil.isLoaded(found.getPost(), "board")).isTrue();
        assertThat(found.getPost().getBoard().getBoardName()).isEqualTo("Popular Board");
    }

    private User persistUser() {
        User user = User.builder()
                .loginId("popular-user")
                .password("password")
                .email("popular@test.com")
                .displayName("Popular User")
                .build();
        entityManager.persist(user);
        return user;
    }

    private Board persistBoard(User creator) {
        Board board = Board.builder()
                .boardName("Popular Board")
                .boardUrl("popular")
                .creator(creator)
                .build();
        entityManager.persist(board);
        return board;
    }

    private Post persistPost(User user, Board board) {
        Post post = Post.builder()
                .user(user)
                .board(board)
                .title("Popular Post")
                .contents("contents")
                .build();
        entityManager.persist(post);
        return post;
    }
}
