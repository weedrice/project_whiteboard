package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardAccessPolicyTest {

    @Mock
    private AdminRepository adminRepository;

    private BoardAccessPolicy boardAccessPolicy;
    private User creator;
    private User manager;

    @BeforeEach
    void setUp() {
        boardAccessPolicy = new BoardAccessPolicy(adminRepository);
        creator = user(1L, "creator");
        manager = user(2L, "manager");
    }

    @Test
    @DisplayName("Public active board read skips admin lookup")
    void canReadBoard_publicActiveBoardSkipsAdminLookup() {
        Board board = board("free", true, true);

        boolean readable = boardAccessPolicy.canReadBoard(board, null);

        assertThat(readable).isTrue();
        verifyNoInteractions(adminRepository);
    }

    @Test
    @DisplayName("Private inactive board read checks admin access once")
    void canReadBoard_privateInactiveBoardChecksAdminAccessOnce() {
        Board board = board("hidden", false, false);
        when(adminRepository.existsByUserAndBoardAndIsActive(manager, board, true)).thenReturn(true);

        boolean readable = boardAccessPolicy.canReadBoard(board, manager);

        assertThat(readable).isTrue();
        verify(adminRepository).existsByUserAndBoardAndIsActive(manager, board, true);
    }

    @Test
    @DisplayName("Public active board write skips admin lookup")
    void canWriteBoard_publicActiveBoardSkipsAdminLookup() {
        Board board = board("free", true, true);

        boolean writable = boardAccessPolicy.canWriteBoard(board, manager);

        assertThat(writable).isTrue();
        verifyNoInteractions(adminRepository);
    }

    @Test
    @DisplayName("Private inactive board write checks admin access once")
    void canWriteBoard_privateInactiveBoardChecksAdminAccessOnce() {
        Board board = board("hidden", false, false);
        when(adminRepository.existsByUserAndBoardAndIsActive(manager, board, true)).thenReturn(true);

        boolean writable = boardAccessPolicy.canWriteBoard(board, manager);

        assertThat(writable).isTrue();
        verify(adminRepository).existsByUserAndBoardAndIsActive(manager, board, true);
    }

    @Test
    @DisplayName("Active inquiry board write skips admin lookup")
    void canWriteBoard_activeInquiryBoardSkipsAdminLookup() {
        Board board = board("inquiry", true, false);

        boolean writable = boardAccessPolicy.canWriteBoard(board, manager);

        assertThat(writable).isTrue();
        verifyNoInteractions(adminRepository);
    }

    private Board board(String boardUrl, boolean isActive, boolean isPublic) {
        Board board = Board.builder()
                .boardName(boardUrl)
                .boardUrl(boardUrl)
                .creator(creator)
                .isPublic(isPublic)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        ReflectionTestUtils.setField(board, "isActive", isActive);
        return board;
    }

    private User user(Long userId, String loginId) {
        User user = User.builder()
                .loginId(loginId)
                .password("password")
                .email(loginId + "@test.com")
                .displayName(loginId)
                .build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
