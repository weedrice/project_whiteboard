package com.weedrice.whiteboard.domain.board.repository;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
class BoardRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private BoardRepository boardRepository;

    private User creator;
    private Board board;

    @BeforeEach
    void setUp() {
        creator = User.builder()
                .loginId("creator")
                .email("creator@test.com")
                .password("password")
                .displayName("Creator")
                .build();
        entityManager.persist(creator);

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .creator(creator)
                .build();
        entityManager.persist(board);
        entityManager.flush();
    }

    @Test
    @DisplayName("게시판 URL로 조회 성공")
    void findByBoardUrl_success() {
        // when
        Optional<Board> found = boardRepository.findByBoardUrl("test-board");

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getBoardName()).isEqualTo("Test Board");
    }

    @Test
    @DisplayName("게시판 저장 및 조회 성공")
    void saveAndFind_success() {
        // given
        Board newBoard = Board.builder()
                .boardName("New Board")
                .boardUrl("new-board")
                .creator(creator)
                .build();

        // when
        Board saved = boardRepository.save(newBoard);
        Optional<Board> found = boardRepository.findById(saved.getBoardId());

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getBoardName()).isEqualTo("New Board");
    }
}
