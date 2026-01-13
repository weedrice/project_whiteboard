package com.weedrice.whiteboard.domain.board.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class BoardEntityTest {

    @Test
    @DisplayName("Board 생성 및 업데이트")
    void boardTest() {
        User user = User.builder().build();
        Board board = Board.builder()
                .boardName("Old Name")
                .description("Old Desc")
                .boardUrl("old-url")
                .creator(user)
                .build();

        assertThat(board.getIsActive()).isTrue(); // 기본값

        // boardName, description, iconUrl, sortOrder, allowNsfw, isActive
        board.update("New Name", "New Desc", "icon.png", 10, true, true);

        assertThat(board.getBoardName()).isEqualTo("New Name");
        assertThat(board.getDescription()).isEqualTo("New Desc");
        assertThat(board.getIconUrl()).isEqualTo("icon.png");
        assertThat(board.getSortOrder()).isEqualTo(10);
        assertThat(board.getAllowNsfw()).isTrue();
    }

    @Test
    @DisplayName("Board URL 업데이트")
    void updateBoardUrl() {
        Board board = Board.builder().boardUrl("old").build();
        board.updateBoardUrl("new");
        assertThat(board.getBoardUrl()).isEqualTo("new");
    }

    @Test
    @DisplayName("Board 비활성화")
    void deactivateBoard() {
        Board board = Board.builder().build();
        board.deactivate();
        assertThat(board.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("BoardCategory 생성 및 업데이트")
    void categoryTest() {
        Board board = Board.builder().build();
        BoardCategory category = BoardCategory.builder()
                .board(board)
                .name("Old Cat")
                .minWriteRole("USER")
                .build();

        assertThat(category.getIsActive()).isTrue();

        category.update("New Cat", 5, "ADMIN");
        
        assertThat(category.getName()).isEqualTo("New Cat");
        assertThat(category.getSortOrder()).isEqualTo(5);
        assertThat(category.getMinWriteRole()).isEqualTo("ADMIN");
    }

    @Test
    @DisplayName("BoardCategory 비활성화")
    void deactivateCategory() {
        BoardCategory category = BoardCategory.builder().build();
        category.deactivate();
        assertThat(category.getIsActive()).isFalse();
    }
}
