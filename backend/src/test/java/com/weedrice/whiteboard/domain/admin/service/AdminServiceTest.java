package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private AdminService adminService;

    private User user;
    private User anotherUser;
    private Board board;
    private Admin admin;

    @BeforeEach
    void setUp() {
        user = User.builder().loginId("testUser").build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        anotherUser = User.builder().loginId("anotherUser").build();
        ReflectionTestUtils.setField(anotherUser, "userId", 2L);

        board = Board.builder()
                .boardName("test")
                .boardUrl("test")
                .creator(user)
                .build();
        ReflectionTestUtils.setField(board, "boardId", 10L);

        admin = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
    }

    @Test
    @DisplayName("게시판 관리자를 교체하면 기존 활성 관리자를 비활성화하고 새로 등록한다")
    void createAdmin_replaceBoardManager() {
        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));

        Admin currentManager = Admin.builder().user(anotherUser).board(board).role(Role.BOARD_ADMIN).build();
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.of(currentManager));
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of(currentManager));
        when(adminRepository.findByUserAndBoardAndRole(user, board, Role.BOARD_ADMIN))
                .thenReturn(Optional.empty());
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        AdminResponse response = adminService.createAdmin("testUser", 10L, Role.BOARD_ADMIN);

        assertThat(response.getRole()).isEqualTo(Role.BOARD_ADMIN);
        assertThat(currentManager.getIsActive()).isFalse();
        verify(adminRepository).save(any(Admin.class));
    }

    @Test
    @DisplayName("기존 비활성 관리자 행이 있으면 재활성화해서 재사용한다")
    void createAdmin_reuseInactiveRow() {
        when(userRepository.findByLoginId("testUser")).thenReturn(Optional.of(user));
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.empty());
        when(adminRepository.findByBoardAndRoleAndIsActive(board, Role.BOARD_ADMIN, true))
                .thenReturn(List.of());

        Admin inactiveManager = Admin.builder().user(user).board(board).role(Role.BOARD_ADMIN).build();
        inactiveManager.deactivate();
        when(adminRepository.findByUserAndBoardAndRole(user, board, Role.BOARD_ADMIN))
                .thenReturn(Optional.of(inactiveManager));

        AdminResponse response = adminService.createAdmin("testUser", 10L, Role.BOARD_ADMIN);

        assertThat(response.getRole()).isEqualTo(Role.BOARD_ADMIN);
        assertThat(inactiveManager.getIsActive()).isTrue();
        verify(adminRepository, never()).save(any(Admin.class));
    }

    @Test
    @DisplayName("게시판 관리자 조회 성공")
    void getBoardManager_success() {
        when(boardRepository.findById(10L)).thenReturn(Optional.of(board));
        when(adminRepository.findFirstByBoardAndRoleAndIsActiveOrderByAdminIdDesc(board, Role.BOARD_ADMIN, true))
                .thenReturn(Optional.of(admin));

        AdminResponse response = adminService.getBoardManager(10L);

        assertThat(response).isNotNull();
        assertThat(response.getRole()).isEqualTo(Role.BOARD_ADMIN);
    }

    @Test
    @DisplayName("슈퍼 관리자 생성 성공")
    void createSuperAdmin_success() {
        String loginId = "testUser";
        User normalUser = User.builder().loginId(loginId).build();
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(normalUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse superAdmin = adminService.createSuperAdmin(loginId);

        assertThat(superAdmin.isSuperAdmin()).isTrue();
    }

    @Test
    @DisplayName("슈퍼 관리자 해제 성공")
    void deactiveSuperAdmin_success() {
        String loginId = "testUser";
        User superAdminUser = User.builder().loginId(loginId).build();
        superAdminUser.grantSuperAdminRole();
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(superAdminUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse normalUser = adminService.deactiveSuperAdmin(loginId);

        assertThat(normalUser.isSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("슈퍼 관리자 목록 조회 성공")
    void getSuperAdmin_success() {
        User superAdmin1 = User.builder().loginId("super1").build();
        superAdmin1.grantSuperAdminRole();
        User superAdmin2 = User.builder().loginId("super2").build();
        superAdmin2.grantSuperAdminRole();
        when(userRepository.findByIsSuperAdminTrue()).thenReturn(List.of(superAdmin1, superAdmin2));

        List<SuperAdminResponse> superAdmins = adminService.getSuperAdmin();

        assertThat(superAdmins).hasSize(2);
        assertThat(superAdmins.get(0).getLoginId()).isEqualTo("super1");
        assertThat(superAdmins.get(1).getLoginId()).isEqualTo("super2");
    }

    @Test
    @DisplayName("모든 관리자 목록 조회 성공")
    void getAllAdmins_success() {
        when(adminRepository.findAll()).thenReturn(List.of(admin));

        List<AdminResponse> admins = adminService.getAllAdmins();

        assertThat(admins).hasSize(1);
        verify(adminRepository).findAll();
    }

    @Test
    @DisplayName("관리자 비활성화 성공")
    void deactivateAdmin_success() {
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        adminService.deactivateAdmin(1L);

        assertThat(admin.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("관리자 활성화 성공")
    void activateAdmin_success() {
        admin.deactivate();
        when(adminRepository.findById(1L)).thenReturn(Optional.of(admin));

        adminService.activateAdmin(1L);

        assertThat(admin.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("대시보드 통계 조회 성공")
    void getDashboardStats_success() {
        when(postRepository.count()).thenReturn(100L);
        when(reportRepository.countByStatus("PENDING")).thenReturn(5L);
        when(userRepository.count()).thenReturn(50L);
        when(userRepository.countByLastLoginAtAfter(any(java.time.LocalDateTime.class))).thenReturn(10L);

        var stats = adminService.getDashboardStats();

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalUsers()).isEqualTo(50L);
        assertThat(stats.getTotalPosts()).isEqualTo(100L);
        assertThat(stats.getPendingReports()).isEqualTo(5L);
        verify(postRepository).count();
        verify(reportRepository).countByStatus("PENDING");
        verify(userRepository).count();
        verify(userRepository).countByLastLoginAtAfter(any(java.time.LocalDateTime.class));
    }
}
