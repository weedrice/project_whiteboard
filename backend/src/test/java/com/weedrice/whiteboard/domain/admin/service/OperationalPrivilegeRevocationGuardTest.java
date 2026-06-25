package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OperationalPrivilegeRevocationGuardTest {

    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;
    @Mock
    private BoardRepository boardRepository;

    private OperationalPrivilegeRevocationGuard guard;
    private User user;
    private Board board;
    private Admin boardAdmin;

    @BeforeEach
    void setUp() {
        guard = new OperationalPrivilegeRevocationGuard(userRepository, adminRepository, boardRepository);

        user = User.builder()
                .loginId("admin")
                .email("admin@test.com")
                .password("password")
                .displayName("Admin")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        board = Board.builder()
                .boardName("board")
                .boardUrl("board")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        boardAdmin = Admin.builder()
                .user(user)
                .board(board)
                .role(Role.BOARD_ADMIN)
                .build();
        ReflectionTestUtils.setField(boardAdmin, "adminId", 100L);
    }

    @Test
    @DisplayName("마지막 사용 가능한 슈퍼 관리자는 회수할 수 없다")
    void validateSuperAdminCanBeRevoked_lastUsableSuperAdmin_forbidden() {
        user.grantSuperAdminRole();
        when(userRepository.findUsableSuperAdminsForUpdate()).thenReturn(List.of(user));

        assertThatThrownBy(() -> guard.validateSuperAdminCanBeRevoked(user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);
    }

    @Test
    @DisplayName("다른 사용 가능한 슈퍼 관리자가 있으면 회수할 수 있다")
    void validateSuperAdminCanBeRevoked_otherUsableSuperAdminExists_success() {
        user.grantSuperAdminRole();
        User other = User.builder().loginId("other").build();
        other.grantSuperAdminRole();
        when(userRepository.findUsableSuperAdminsForUpdate()).thenReturn(List.of(user, other));

        guard.validateSuperAdminCanBeRevoked(user);

        verify(userRepository).findUsableSuperAdminsForUpdate();
    }

    @Test
    @DisplayName("사용 불가능한 슈퍼 관리자 권한 정리는 마지막 관리자 검증을 건너뛴다")
    void validateSuperAdminCanBeRevoked_unusableSuperAdmin_skipsUsableCount() {
        user.grantSuperAdminRole();
        user.suspend();

        guard.validateSuperAdminCanBeRevoked(user);

        verify(userRepository, never()).findUsableSuperAdminsForUpdate();
    }

    @Test
    @DisplayName("현재 슈퍼 관리자는 자신의 권한을 회수할 수 없다")
    void validateSuperAdminCanBeRevokedBy_selfSuperAdmin_forbidden() {
        user.grantSuperAdminRole();

        assertThatThrownBy(() -> guard.validateSuperAdminCanBeRevokedBy(user, 1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        verify(userRepository, never()).findUsableSuperAdminsForUpdate();
    }

    @Test
    @DisplayName("다른 슈퍼 관리자는 기존 회수 가드를 통과해야 한다")
    void validateSuperAdminCanBeRevokedBy_otherSuperAdmin_usesUsableCountGuard() {
        user.grantSuperAdminRole();
        User other = User.builder().loginId("other").build();
        other.grantSuperAdminRole();
        when(userRepository.findUsableSuperAdminsForUpdate()).thenReturn(List.of(user, other));

        guard.validateSuperAdminCanBeRevokedBy(user, 2L);

        verify(userRepository).findUsableSuperAdminsForUpdate();
    }

    @Test
    @DisplayName("노드의 마지막 활성 매니저는 단건 회수할 수 없다")
    void validateBoardAdminCanBeRevoked_lastActiveBoardAdmin_validationError() {
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.countByBoardAndRoleAndIsActiveAndAdminIdNot(
                board, Role.BOARD_ADMIN, true, 100L))
                .thenReturn(0L);

        assertThatThrownBy(() -> guard.validateBoardAdminCanBeRevoked(boardAdmin))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("다른 활성 매니저가 있으면 노드 매니저를 단건 회수할 수 있다")
    void validateBoardAdminCanBeRevoked_otherActiveBoardAdminExists_success() {
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.countByBoardAndRoleAndIsActiveAndAdminIdNot(
                board, Role.BOARD_ADMIN, true, 100L))
                .thenReturn(1L);

        guard.validateBoardAdminCanBeRevoked(boardAdmin);

        verify(adminRepository).countByBoardAndRoleAndIsActiveAndAdminIdNot(
                board, Role.BOARD_ADMIN, true, 100L);
    }

    @Test
    @DisplayName("일괄 권한 정리도 노드별 남는 활성 매니저를 요구한다")
    void validateOperationalPrivilegesCanBeRevoked_batchLastBoardAdmin_validationError() {
        when(boardRepository.findByBoardIdInForUpdate(List.of(10L))).thenReturn(List.of(board));
        when(adminRepository.countActiveAdminsByBoardIdsExcludingAdminIds(
                List.of(10L),
                Role.BOARD_ADMIN,
                true,
                List.of(100L)))
                .thenReturn(List.of());

        assertThatThrownBy(() -> guard.validateOperationalPrivilegesCanBeRevoked(user, List.of(boardAdmin)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("일괄 권한 정리에서 다른 활성 매니저가 남으면 통과한다")
    void validateOperationalPrivilegesCanBeRevoked_batchOtherBoardAdminExists_success() {
        when(boardRepository.findByBoardIdInForUpdate(List.of(10L))).thenReturn(List.of(board));
        when(adminRepository.countActiveAdminsByBoardIdsExcludingAdminIds(
                List.of(10L),
                Role.BOARD_ADMIN,
                true,
                List.of(100L)))
                .thenReturn(List.of(boardAdminCount(10L, 1L)));

        guard.validateOperationalPrivilegesCanBeRevoked(user, List.of(boardAdmin));

        verify(adminRepository).countActiveAdminsByBoardIdsExcludingAdminIds(
                List.of(10L),
                Role.BOARD_ADMIN,
                true,
                List.of(100L));
    }

    private AdminRepository.BoardAdminCountProjection boardAdminCount(Long boardId, long adminCount) {
        return new AdminRepository.BoardAdminCountProjection() {
            @Override
            public Long getBoardId() {
                return boardId;
            }

            @Override
            public long getAdminCount() {
                return adminCount;
            }
        };
    }
}
