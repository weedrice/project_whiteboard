package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BoardManagerAssignmentServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private AdminEligibleUserService adminEligibleUserService;

    @InjectMocks
    private BoardManagerAssignmentService boardManagerAssignmentService;

    private User user;
    private User anotherUser;
    private Board board;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testUser")
                .password("encoded")
                .email("test@example.com")
                .displayName("test")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        anotherUser = User.builder()
                .loginId("anotherUser")
                .password("encoded")
                .email("another@example.com")
                .displayName("another")
                .build();
        ReflectionTestUtils.setField(anotherUser, "userId", 2L);

        board = Board.builder()
                .boardName("test")
                .boardUrl("test")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);
    }

    @Test
    @DisplayName("게시판 관리자 교체 시 기존 활성 관리자를 비활성화하고 신규 관리자를 저장한다")
    void assignBoardManager_replaceBoardManager() {
        Admin currentManager = Admin.builder().user(anotherUser).board(board).role(Role.BOARD_ADMIN).build();

        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of(currentManager));
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, Role.BOARD_ADMIN, false))
                .thenReturn(Optional.empty());
        when(adminRepository.saveAndFlush(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        Admin assignedManager = boardManagerAssignmentService.assignBoardManager(board, user);

        assertThat(assignedManager.getRole()).isEqualTo(Role.BOARD_ADMIN);
        assertThat(currentManager.getIsActive()).isFalse();
        verify(adminRepository).saveAndFlush(any(Admin.class));
    }

    @Test
    @DisplayName("비활성 관리자 이력이 있으면 재사용한다")
    void assignBoardManager_reusesInactiveRow() {
        Admin inactiveManager = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        inactiveManager.deactivate();

        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of());
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, Role.BOARD_ADMIN, false))
                .thenReturn(Optional.of(inactiveManager));

        Admin assignedManager = boardManagerAssignmentService.assignBoardManager(board, user);

        assertThat(assignedManager).isSameAs(inactiveManager);
        assertThat(inactiveManager.getIsActive()).isTrue();
        verify(adminRepository).flush();
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
    }

    @Test
    @DisplayName("이미 대상 사용자가 board admin으로 활성 상태여도 나머지 활성 row는 정리한다")
    void assignBoardManager_sameUserCleansUpDuplicateActiveRows() {
        Admin olderDuplicate = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(olderDuplicate, "adminId", 90L);
        Admin latestDuplicate = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(latestDuplicate, "adminId", 110L);
        Admin anotherActiveManager = Admin.builder().user(anotherUser).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(anotherActiveManager, "adminId", 120L);

        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of(olderDuplicate, latestDuplicate, anotherActiveManager));

        Admin assignedManager = boardManagerAssignmentService.assignBoardManager(board, user);

        assertThat(assignedManager).isSameAs(latestDuplicate);
        assertThat(latestDuplicate.getIsActive()).isTrue();
        assertThat(olderDuplicate.getIsActive()).isFalse();
        assertThat(anotherActiveManager.getIsActive()).isFalse();
        verify(adminRepository).flush();
    }

    @Test
    @DisplayName("비활성 사용자는 게시판 관리자로 배정할 수 없다")
    void assignBoardManager_rejectsInactiveUser() {
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(adminEligibleUserService)
                .validateActiveUser(user);

        assertThatThrownBy(() -> boardManagerAssignmentService.assignBoardManager(board, user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(adminRepository, never()).findByBoardAndRoleAndIsActive(any(), any(), any());
    }

    @Test
    @DisplayName("BOARD_ADMIN 재활성화 시 기존 활성 관리자를 비활성화한다")
    void activateBoardManager_deactivatesExistingManagers() {
        Admin inactiveAdmin = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        inactiveAdmin.deactivate();
        ReflectionTestUtils.setField(inactiveAdmin, "adminId", 100L);

        Admin currentManager = Admin.builder().user(anotherUser).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(currentManager, "adminId", 200L);

        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of(currentManager));

        Admin activatedManager = boardManagerAssignmentService.activateBoardManager(inactiveAdmin, board);

        assertThat(activatedManager).isSameAs(inactiveAdmin);
        assertThat(inactiveAdmin.getIsActive()).isTrue();
        assertThat(currentManager.getIsActive()).isFalse();
        verify(adminRepository).flush();
    }

    @Test
    @DisplayName("동시성으로 게시판 관리자 저장이 충돌하면 DUPLICATE_RESOURCE로 변환한다")
    void assignBoardManager_duplicateSave_throwsBusinessException() {
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of());
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, Role.BOARD_ADMIN, false))
                .thenReturn(Optional.empty());
        when(adminRepository.saveAndFlush(any(Admin.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate board admin"));

        assertThatThrownBy(() -> boardManagerAssignmentService.assignBoardManager(board, user))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }
}
