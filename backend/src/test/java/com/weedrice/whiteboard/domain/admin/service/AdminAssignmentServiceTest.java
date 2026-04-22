package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.AdminRole;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
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
class AdminAssignmentServiceTest {

    @Mock
    private AdminRepository adminRepository;

    @Mock
    private BoardRepository boardRepository;

    @Mock
    private AdminEligibleUserService adminEligibleUserService;

    @Mock
    private BoardManagerAssignmentService boardManagerAssignmentService;

    @InjectMocks
    private AdminAssignmentService adminAssignmentService;

    private User user;
    private Board board;
    private Admin admin;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testUser")
                .password("encoded")
                .email("test@example.com")
                .displayName("test")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

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
    @DisplayName("게시판 관리자 생성은 공통 배정 서비스로 위임한다")
    void createAdmin_boardAdmin_delegatesToBoardManagerAssignmentService() {
        when(adminEligibleUserService.getActiveUserByLoginId("testUser")).thenReturn(user);
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(boardManagerAssignmentService.assignBoardManager(board, user)).thenReturn(admin);

        AdminResponse response = adminAssignmentService.createAdmin("testUser", 10L, AdminRole.BOARD_ADMIN);

        assertThat(response.getAdminId()).isEqualTo(100L);
        assertThat(response.getRole()).isEqualTo(Role.BOARD_ADMIN);
        verify(boardManagerAssignmentService).assignBoardManager(board, user);
    }

    @Test
    @DisplayName("role 이 없으면 검증 오류를 반환한다")
    void createAdmin_requiresRole() {
        assertThatThrownBy(() -> adminAssignmentService.createAdmin("testUser", 10L, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(adminEligibleUserService, never()).getActiveUserByLoginId(any());
        verify(boardRepository, never()).findByIdForUpdate(any());
    }

    @Test
    @DisplayName("비활성 사용자는 관리자 배정 대상이 될 수 없다")
    void createAdmin_rejectsInactiveCandidate() {
        when(adminEligibleUserService.getActiveUserByLoginId("testUser"))
                .thenThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE));

        assertThatThrownBy(() -> adminAssignmentService.createAdmin("testUser", 10L, AdminRole.BOARD_ADMIN))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);

        verify(boardRepository, never()).findByIdForUpdate(any());
        verify(boardManagerAssignmentService, never()).assignBoardManager(any(), any());
    }

    @Test
    @DisplayName("일반 관리자도 비활성 이력이 있으면 재활성화한다")
    void createAdmin_reuseInactiveScopedAdmin() {
        Admin inactiveAdmin = Admin.builder().user(user).board(board).role("MODERATOR").build();
        inactiveAdmin.deactivate();

        when(adminEligibleUserService.getActiveUserByLoginId("testUser")).thenReturn(user);
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", true))
                .thenReturn(Optional.empty());
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", false))
                .thenReturn(Optional.of(inactiveAdmin));

        AdminResponse response = adminAssignmentService.createAdmin("testUser", 10L, AdminRole.MODERATOR);

        assertThat(response.getRole()).isEqualTo("MODERATOR");
        assertThat(inactiveAdmin.getIsActive()).isTrue();
        verify(adminRepository).flush();
        verify(adminRepository, never()).saveAndFlush(any(Admin.class));
    }

    @Test
    @DisplayName("동시성으로 일반 관리자 중복 저장이 발생하면 DUPLICATE_RESOURCE로 변환한다")
    void createAdmin_duplicateScopedAdmin_throwsBusinessException() {
        when(adminEligibleUserService.getActiveUserByLoginId("testUser")).thenReturn(user);
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", true))
                .thenReturn(Optional.empty());
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", false))
                .thenReturn(Optional.empty());
        when(adminRepository.saveAndFlush(any(Admin.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate admin"));

        assertThatThrownBy(() -> adminAssignmentService.createAdmin("testUser", 10L, AdminRole.MODERATOR))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("일반 관리자 생성 시 활성 row가 있으면 비활성 이력이 있어도 중복으로 거부한다")
    void createAdmin_scopedAdmin_prefersActiveDuplicateCheck() {
        Admin activeAdmin = Admin.builder().user(user).board(board).role("MODERATOR").build();
        ReflectionTestUtils.setField(activeAdmin, "adminId", 400L);

        when(adminEligibleUserService.getActiveUserByLoginId("testUser")).thenReturn(user);
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByUserAndBoardAndRoleAndIsActiveOrderByAdminIdDesc(
                user, board, "MODERATOR", true))
                .thenReturn(Optional.of(activeAdmin));

        assertThatThrownBy(() -> adminAssignmentService.createAdmin("testUser", 10L, AdminRole.MODERATOR))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);

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

        assertThatThrownBy(() -> adminAssignmentService.activateAdmin(300L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.DUPLICATE_RESOURCE);
    }

    @Test
    @DisplayName("BOARD_ADMIN 활성화는 공통 배정 서비스로 위임한다")
    void activateAdmin_boardAdmin_delegatesToBoardManagerAssignmentService() {
        admin.deactivate();

        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));

        adminAssignmentService.activateAdmin(1L);

        verify(boardManagerAssignmentService).activateBoardManager(admin, board);
    }

    @Test
    @DisplayName("일반 관리자 활성화 전에 대상 사용자의 활성 상태를 검증한다")
    void activateAdmin_scopedAdmin_rejectsInactiveUser() {
        Admin inactiveAdmin = Admin.builder().user(user).board(board).role("MODERATOR").build();
        inactiveAdmin.deactivate();
        ReflectionTestUtils.setField(inactiveAdmin, "adminId", 300L);

        when(adminRepository.findById(300L)).thenReturn(Optional.of(inactiveAdmin));
        when(boardRepository.findByIdForUpdate(10L)).thenReturn(Optional.of(board));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE))
                .when(adminEligibleUserService)
                .validateActiveUser(user);

        assertThatThrownBy(() -> adminAssignmentService.activateAdmin(300L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);
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
}
