package com.weedrice.whiteboard.domain.post.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManagerFactory;
import jakarta.persistence.PersistenceUnitUtil;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class DraftPostRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private EntityManagerFactory entityManagerFactory;

    @Autowired
    private DraftPostRepository draftPostRepository;

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("draft-user")
                .email("draft@test.com")
                .password("password")
                .displayName("초안유저")
                .build();
        entityManager.persist(user);

        Board board = Board.builder()
                .boardName("draft-board")
                .boardUrl("draft-board")
                .creator(user)
                .build();
        entityManager.persist(board);

        entityManager.persist(DraftPost.builder()
                .user(user)
                .board(board)
                .title("draft")
                .contents("contents")
                .build());
        entityManager.flush();
        entityManager.clear();
    }

    @Test
    @DisplayName("초안 목록 전용 조회는 board를 함께 로드한다")
    void findPageByUserWithBoard_fetchesBoard() {
        Page<DraftPost> result = draftPostRepository.findPageByUserWithBoard(user, PageRequest.of(0, 10));
        PersistenceUnitUtil persistenceUnitUtil = entityManagerFactory.getPersistenceUnitUtil();

        assertThat(result.getContent()).hasSize(1);
        DraftPost draftPost = result.getContent().getFirst();
        assertThat(persistenceUnitUtil.isLoaded(draftPost, "board")).isTrue();
        assertThat(draftPost.getBoard().getBoardName()).isEqualTo("draft-board");
    }
}
