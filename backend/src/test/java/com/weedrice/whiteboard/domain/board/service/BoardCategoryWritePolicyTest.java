package com.weedrice.whiteboard.domain.board.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardCategoryWritePolicyTest {

    @Mock
    private BoardAccessPolicy boardAccessPolicy;

    private BoardCategoryWritePolicy boardCategoryWritePolicy;
    private Board board;
    private User user;

    @BeforeEach
    void setUp() {
        boardCategoryWritePolicy = new BoardCategoryWritePolicy(boardAccessPolicy);
        board = Board.builder()
                .boardName("Free")
                .boardUrl("free")
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
        user = User.builder()
                .loginId("user")
                .password("password")
                .email("user@example.com")
                .displayName("User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
    }

    @Test
    void canWriteResolvedRole_allowsUserRoleWithoutBoardAdminLookup() {
        assertThat(boardCategoryWritePolicy.canWriteResolvedRole(board, user, Role.USER)).isTrue();

        verifyNoInteractions(boardAccessPolicy);
    }

    @Test
    void canWriteResolvedRole_usesSuppliedAdminBoardIdsForBoardAdminRole() {
        Set<Long> adminBoardIds = Set.of(10L);
        when(boardAccessPolicy.hasBoardAdminAccess(board, user, adminBoardIds)).thenReturn(true);

        assertThat(boardCategoryWritePolicy.canWriteResolvedRole(board, user, Role.BOARD_ADMIN, adminBoardIds))
                .isTrue();

        verify(boardAccessPolicy).hasBoardAdminAccess(board, user, adminBoardIds);
    }

    @Test
    void canWriteResolvedRole_requiresUsableSuperAdminForSuperAdminRole() {
        user.grantSuperAdminRole();

        assertThat(boardCategoryWritePolicy.canWriteResolvedRole(board, user, Role.SUPER_ADMIN)).isTrue();

        user.suspend();

        assertThat(boardCategoryWritePolicy.canWriteResolvedRole(board, user, Role.SUPER_ADMIN)).isFalse();
    }

    @Test
    void validateWriteRole_rejectsInvalidRole() {
        assertThatThrownBy(() -> boardCategoryWritePolicy.validateWriteRole(board, user, "BOARD_ADMINN"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_INPUT_VALUE);
    }

    @Test
    void canWriteLenientRole_allowsLegacyUnknownRoleWithoutLookup() {
        assertThat(boardCategoryWritePolicy.canWriteLenientRole(board, user, "LEGACY", Set.of())).isTrue();

        verifyNoInteractions(boardAccessPolicy);
    }
}
