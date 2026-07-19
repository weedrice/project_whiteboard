package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.OperationalPrivilegeRevocationGuard;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.InOrder;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class UserPrivilegeCleanupServiceTest {

    @Mock private AdminRepository adminRepository;
    @Mock private BoardRepository boardRepository;
    @Mock private OperationalPrivilegeRevocationGuard privilegeRevocationGuard;

    @InjectMocks
    private UserPrivilegeCleanupService userPrivilegeCleanupService;

    @Test
    void removeOperationalPrivileges_locksBoardsBeforeAdminRows() {
        User user = User.builder().build();
        Board board = Board.builder().creator(user).build();
        Admin admin = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        when(adminRepository.findActiveBoardIdsByUser(user)).thenReturn(List.of(10L));
        when(boardRepository.findByBoardIdInForUpdate(List.of(10L))).thenReturn(List.of(board));
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(user, true)).thenReturn(List.of(admin));

        userPrivilegeCleanupService.removeOperationalPrivileges(user);

        InOrder ordered = inOrder(adminRepository, boardRepository);
        ordered.verify(adminRepository).findActiveBoardIdsByUser(user);
        ordered.verify(boardRepository).findByBoardIdInForUpdate(List.of(10L));
        ordered.verify(adminRepository).findAllByUserAndIsActiveOrderByAdminIdAsc(user, true);
    }

    @Test
    void removeOperationalPrivileges_revokesSuperAdminAndDeactivatesActiveAdmins() {
        User user = User.builder().build();
        user.grantSuperAdminRole();
        Admin firstAdmin = Admin.builder().user(user).role(Role.BOARD_ADMIN).build();
        Admin secondAdmin = Admin.builder().user(user).role(AdminRole.MODERATOR.name()).build();
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(user, true))
                .thenReturn(List.of(firstAdmin, secondAdmin));

        userPrivilegeCleanupService.removeOperationalPrivileges(user);

        assertThat(user.getIsSuperAdmin()).isFalse();
        assertThat(firstAdmin.getIsActive()).isFalse();
        assertThat(secondAdmin.getIsActive()).isFalse();
        verify(adminRepository, never()).findByUserAndIsActiveOrderByAdminIdAsc(user, true);
        verify(adminRepository).findAllByUserAndIsActiveOrderByAdminIdAsc(user, true);
        verify(privilegeRevocationGuard).validateOperationalPrivilegesCanBeRevoked(
                user, List.of(firstAdmin, secondAdmin));
    }

    @Test
    void removeOperationalPrivileges_doesNotMutateWhenGuardRejects() {
        User user = User.builder().build();
        user.grantSuperAdminRole();
        Admin admin = Admin.builder().user(user).role(Role.BOARD_ADMIN).build();
        List<Admin> activeAdmins = List.of(admin);
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(user, true))
                .thenReturn(activeAdmins);
        doThrow(new BusinessException(ErrorCode.FORBIDDEN))
                .when(privilegeRevocationGuard)
                .validateOperationalPrivilegesCanBeRevoked(user, activeAdmins);

        assertThatThrownBy(() -> userPrivilegeCleanupService.removeOperationalPrivileges(user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        assertThat(user.getIsSuperAdmin()).isTrue();
        assertThat(admin.getIsActive()).isTrue();
    }

    @Test
    void removeOperationalPrivilegesWithActor_usesActorAwareGuard() {
        User user = User.builder().build();
        user.grantSuperAdminRole();
        Admin admin = Admin.builder().user(user).role(Role.BOARD_ADMIN).build();
        List<Admin> activeAdmins = List.of(admin);
        when(adminRepository.findAllByUserAndIsActiveOrderByAdminIdAsc(user, true))
                .thenReturn(activeAdmins);

        userPrivilegeCleanupService.removeOperationalPrivileges(user, 9L);

        assertThat(user.getIsSuperAdmin()).isFalse();
        assertThat(admin.getIsActive()).isFalse();
        verify(privilegeRevocationGuard).validateOperationalPrivilegesCanBeRevokedBy(user, activeAdmins, 9L);
    }
}
