package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminAssignmentServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private UserRepository userRepository;

    @Mock
    private BoardRepository boardRepository;

    @InjectMocks
    private AdminAssignmentService adminAssignmentService;

    private User user;
    private User anotherUser;
    private Board board;
    private Admin admin;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testUser").displayName("test").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        anotherUser = User.builder().loginId("anotherUser").displayName("another").build();
        ReflectionTestUtils.setField(anotherUser, "userId", 2L);

        board = Board.builder()
                .boardName("test")
                .boardUrl("test")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        admin = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(admin, "adminId", 100L);
    }

    @Test
    @DisplayName("게시판 관리자 교체 시 기존 활성 관리자를 비활성화하고 신규 관리자를 저장한다")
    void createAdmin_replaceBoardManager() {
        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));

        Admin currentManager = Admin.builder().user(anotherUser).board(board).role(Role.BOARD_ADMIN).build();
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of(currentManager));
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, Role.BOARD_ADMIN, false))
                .thenReturn(Optional.empty());
        when(adminRepository.saveAndFlush(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminResponse response = adminAssignmentService.createAdmin("testUser", 10L, Role.BOARD_ADMIN);

        assertThat(response.getRole()).isEqualTo(Role.BOARD_ADMIN);
        assertThat(currentManager.getIsActive()).isFalse();
        verify(adminRepository).saveAndFlush(any(Admin.class));
    }

    @Test
    @DisplayName("비활성 관리자 이력이 있으면 재사용한다")
    void createAdmin_reuseInactiveRow() {
        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of());

        Admin inactiveManager = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        inactiveManager.deactivate();
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, Role.BOARD_ADMIN, false))
                .thenReturn(Optional.of(inactiveManager));

        AdminResponse response = adminAssignmentService.createAdmin("testUser", 10L, Role.BOARD_ADMIN);

        assertThat(response.getRole()).isEqualTo(Role.BOARD_ADMIN);
        assertThat(inactiveManager.getIsActive()).isTrue();
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
    }

    @Test
    @DisplayName("일반 관리자도 비활성 이력이 있으면 재활성화한다")
    void createAdmin_reuseInactiveScopedAdmin() {
        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));

        Admin inactiveAdmin = Admin.builder().user(user).board(board).role("MODERATOR").build();
        inactiveAdmin.deactivate();
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", true))
                .thenReturn(Optional.empty());
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", false))
                .thenReturn(Optional.of(inactiveAdmin));

        AdminResponse response = adminAssignmentService.createAdmin("testUser", 10L, "MODERATOR");

        assertThat(response.getRole()).isEqualTo("MODERATOR");
        assertThat(inactiveAdmin.getIsActive()).isTrue();
        verify(adminRepository).flush();
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
    }

    @Test
    @DisplayName("동시성으로 일반 관리자 중복 저장이 발생하면 DUPLICATE_RESOURCE로 변환한다")
    void createAdmin_duplicateScopedAdmin_throwsBusinessException() {
        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", true))
                .thenReturn(Optional.empty());
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", false))
                .thenReturn(Optional.empty());
        when(adminRepository.saveAndFlush(any(Admin.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate admin"));

        org.assertj.core.api.ThrowableAssert.ThrowingCallable action =
                () -> adminAssignmentService.createAdmin("testUser", 10L, "MODERATOR");

        org.assertj.core.api.Assertions.assertThatThrownBy(action)
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.weedrice.whiteboard.global.exception.ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("일반 관리자 생성 시 활성 row가 있으면 비활성 이력이 있어도 중복으로 거부한다")
    void createAdmin_scopedAdmin_prefersActiveDuplicateCheck() {
        Admin activeAdmin = Admin.builder().user(user).board(board).role("MODERATOR").build();
        ReflectionTestUtils.setField(activeAdmin, "adminId", 400L);

        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", true))
                .thenReturn(Optional.of(activeAdmin));

        org.assertj.core.api.Assertions.assertThatThrownBy(
                () -> adminAssignmentService.createAdmin("testUser", 10L, "MODERATOR"))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.weedrice.whiteboard.global.exception.ErrorCode.DUPLICATE_RESOURCE);

        verify(adminRepository, never()).findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", false);
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
    }

    @Test
    @DisplayName("일반 관리자 활성화 시 동일 역할의 활성 row가 이미 있으면 거부한다")
    void activateAdmin_scopedAdmin_duplicateActiveRow() {
        Admin inactiveAdmin = Admin.builder().user(user).board(board).role("MODERATOR").build();
        inactiveAdmin.deactivate();
        ReflectionTestUtils.setField(inactiveAdmin, "adminId", 300L);

        Admin activeAdmin = Admin.builder().user(user).board(board).role("MODERATOR").build();
        ReflectionTestUtils.setField(activeAdmin, "adminId", 301L);

        when(adminRepository.findById(300L)).thenReturn(Optional.of(inactiveAdmin));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findByUserAndBoardAndRoleAndIsActive(user, board, "MODERATOR", true))
                .thenReturn(Optional.of(activeAdmin));

        org.assertj.core.api.Assertions.assertThatThrownBy(() -> adminAssignmentService.activateAdmin(300L))
                .isInstanceOf(com.weedrice.whiteboard.global.exception.BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", com.weedrice.whiteboard.global.exception.ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("게시판 관리자 조회 성공")
    void getBoardManager_success() {
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.of(admin));

        AdminResponse response = adminAssignmentService.getBoardManager(10L);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.BOARD_ADMIN);
        assertThat(response.getAdminId()).isEqualTo(100L);
    }

    @Test
    @DisplayName("관리자 비활성화 성공")
    void deactivateAdmin_success() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        adminAssignmentService.deactivateAdmin(1L);

        assertThat(admin.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("관리자 활성화 성공")
    void activateAdmin_success() {
        admin.deactivate();
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true)).thenReturn(List.of());

        adminAssignmentService.activateAdmin(1L);

        assertThat(admin.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("BOARD_ADMIN 재활성화 시 기존 활성 관리자를 비활성화한다")
    void activateAdmin_boardAdmin_deactivatesExistingManagers() {
        admin.deactivate();
        Admin currentManager = Admin.builder().user(anotherUser).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(currentManager, "adminId", 200L);

        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of(currentManager));

        adminAssignmentService.activateAdmin(1L);

        assertThat(admin.getIsActive()).isTrue();
        assertThat(currentManager.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("이미 대상 사용자가 board admin으로 활성 상태여도 나머지 활성 row는 정리한다")
    void createAdmin_boardAdmin_sameUserCleansUpDuplicateActiveRows() {
        Admin olderDuplicate = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(olderDuplicate, "adminId", 90L);
        Admin latestDuplicate = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(latestDuplicate, "adminId", 110L);
        Admin anotherActiveManager = Admin.builder().user(anotherUser).board(board).role(Role.BOARD_ADMIN).build();
        ReflectionTestUtils.setField(anotherActiveManager, "adminId", 120L);

        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of(olderDuplicate, latestDuplicate, anotherActiveManager));

        AdminResponse response = adminAssignmentService.createAdmin("testUser", 10L, Role.BOARD_ADMIN);

        assertThat(response.getAdminId()).isEqualTo(110L);
        assertThat(latestDuplicate.getIsActive()).isTrue();
        assertThat(olderDuplicate.getIsActive()).isFalse();
        assertThat(anotherActiveManager.getIsActive()).isFalse();
        verify(adminRepository).flush();
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
    }
}
