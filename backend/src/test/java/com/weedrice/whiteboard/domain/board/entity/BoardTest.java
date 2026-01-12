package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoardTest {

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

        board = Board.builder()
                .boardName("Test Board")
                .boardUrl("test-board")
                .description("Test Description")
                .creator(creator)
                .iconUrl("icon.png")
                .sortOrder(1)
                .build();
    }

    @Test
    @DisplayName("게시판 생성 시 초기값 확인")
    void createBoard_initialValues() {
        // then
        assertThat(board.getIsActive()).isTrue();
        assertThat(board.getAllowNsfw()).isFalse();
        assertThat(board.getSortOrder()).isEqualTo(1);
    }

    @Test
    @DisplayName("게시판 생성 시 sortOrder 기본값 확인")
    void createBoard_defaultSortOrder() {
        // given
        Board boardWithoutSortOrder = Board.builder()
                .boardName("New Board")
                .boardUrl("new-board")
                .creator(creator)
                .build();

        // then
        assertThat(boardWithoutSortOrder.getSortOrder()).isEqualTo(0);
    }

    @Test
    @DisplayName("게시판 정보 수정")
    void update_success() {
        // when
        board.update("Updated Board", "Updated Description", "new-icon.png", 2, true, false);

        // then
        assertThat(board.getBoardName()).isEqualTo("Updated Board");
        assertThat(board.getDescription()).isEqualTo("Updated Description");
        assertThat(board.getIconUrl()).isEqualTo("new-icon.png");
        assertThat(board.getSortOrder()).isEqualTo(2);
        assertThat(board.getAllowNsfw()).isTrue();
        assertThat(board.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("게시판 URL 변경")
    void updateBoardUrl_success() {
        // when
        board.updateBoardUrl("new-url");

        // then
        assertThat(board.getBoardUrl()).isEqualTo("new-url");
    }

    @Test
    @DisplayName("게시판 비활성화")
    void deactivate_success() {
        // when
        board.deactivate();

        // then
        assertThat(board.getIsActive()).isFalse();
    }
}
