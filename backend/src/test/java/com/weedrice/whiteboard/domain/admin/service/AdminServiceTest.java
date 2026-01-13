package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminServiceTest {

    @Mock
    private AdminRepository adminRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private IpBlockRepository ipBlockRepository;
    @Mock
    private BoardRepository boardRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private ReportRepository reportRepository;

    @InjectMocks
    private AdminService adminService;

    private User user;
    private Admin admin;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        admin = Admin.builder().user(user).role("ADMIN").build();
    }

    @Test
    @DisplayName("관리자 생성 성공")
    void createAdmin_success() {
        // given
        String loginId = "testUser";
        String role = "ADMIN";
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndBoardAndIsActive(user, null, true)).thenReturn(false);
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        // when
        AdminResponse createdAdmin = adminService.createAdmin(loginId, null, role);

        // then
        assertThat(createdAdmin.getRole()).isEqualTo(role);
        assertThat(createdAdmin.getUser().getLoginId()).isEqualTo(user.getLoginId());
    }

    @Test
    @DisplayName("IP 차단 성공")
    void blockIp_success() {
        // given
        Long adminUserId = 1L;
        String ipAddress = "127.0.0.1";
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndIsActive(user, true)).thenReturn(Optional.of(admin));
        when(ipBlockRepository.findByIpAddress(ipAddress)).thenReturn(Optional.empty());
        when(ipBlockRepository.save(any(IpBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        // when
        IpBlockResponse ipBlock = adminService.blockIp(adminUserId, ipAddress, "Test", null);

        // then
        assertThat(ipBlock.getIpAddress()).isEqualTo(ipAddress);
        verify(ipBlockRepository).save(any(IpBlock.class));
    }

    @Test
    @DisplayName("슈퍼 관리자 생성 성공")
    void createSuperAdmin_success() {
        // given
        String loginId = "testUser";
        User normalUser = User.builder().loginId(loginId).build();
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(normalUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        // when
        SuperAdminUpdateResponse superAdmin = adminService.createSuperAdmin(loginId);

        // then
        assertThat(superAdmin.isSuperAdmin()).isEqualTo(true);
    }

    @Test
    @DisplayName("슈퍼 관리자 해제 성공")
    void deactiveSuperAdmin_success() {
        // given
        String loginId = "testUser";
        User superAdminUser = User.builder().loginId(loginId).build();
        superAdminUser.grantSuperAdminRole();
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(superAdminUser));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        // when
        SuperAdminUpdateResponse normalUser = adminService.deactiveSuperAdmin(loginId);

        // then
        assertThat(normalUser.isSuperAdmin()).isEqualTo(false);
    }

    @Test
    @DisplayName("슈퍼 관리자 목록 조회 성공")
    void getSuperAdmin_success() {
        // given
        User superAdmin1 = User.builder().loginId("super1").build();
        superAdmin1.grantSuperAdminRole();
        User superAdmin2 = User.builder().loginId("super2").build();
        superAdmin2.grantSuperAdminRole();
        when(userRepository.findByIsSuperAdminTrue()).thenReturn(List.of(superAdmin1, superAdmin2));

        // when
        List<SuperAdminResponse> superAdmins = adminService.getSuperAdmin();

        // then
        assertThat(superAdmins).hasSize(2);
        assertThat(superAdmins.get(0).getLoginId()).isEqualTo("super1");
        assertThat(superAdmins.get(1).getLoginId()).isEqualTo("super2");
    }

    @Test
    @DisplayName("모든 관리자 목록 조회 성공")
    void getAllAdmins_success() {
        // given
        when(adminRepository.findAll()).thenReturn(List.of(admin));

        // when
        List<AdminResponse> admins = adminService.getAllAdmins();

        // then
        assertThat(admins).hasSize(1);
        verify(adminRepository).findAll();
    }

    @Test
    @DisplayName("관리자 비활성화 성공")
    void deactivateAdmin_success() {
        // given
        Long adminId = 1L;
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // when
        adminService.deactivateAdmin(adminId);

        // then
        assertThat(admin.getIsActive()).isFalse();
    }

    @Test
    @DisplayName("관리자 활성화 성공")
    void activateAdmin_success() {
        // given
        Long adminId = 1L;
        admin.deactivate(); // 먼저 비활성화
        when(adminRepository.findById(adminId)).thenReturn(Optional.of(admin));

        // when
        adminService.activateAdmin(adminId);

        // then
        assertThat(admin.getIsActive()).isTrue();
    }

    @Test
    @DisplayName("IP 차단 해제 성공")
    void unblockIp_success() {
        // given
        String ipAddress = "127.0.0.1";
        com.weedrice.whiteboard.domain.admin.entity.IpBlock ipBlock = com.weedrice.whiteboard.domain.admin.entity.IpBlock.builder()
                .ipAddress(ipAddress)
                .reason("Test")
                .build();
        when(ipBlockRepository.findByIpAddress(ipAddress)).thenReturn(Optional.of(ipBlock));

        // when
        adminService.unblockIp(ipAddress);

        // then
        verify(ipBlockRepository).delete(ipBlock);
    }

    @Test
    @DisplayName("차단된 IP 목록 조회 성공")
    void getBlockedIps_success() {
        // given
        com.weedrice.whiteboard.domain.admin.entity.IpBlock ipBlock = com.weedrice.whiteboard.domain.admin.entity.IpBlock.builder()
                .ipAddress("127.0.0.1")
                .reason("Test")
                .admin(admin)
                .build();
        when(ipBlockRepository.findAll()).thenReturn(List.of(ipBlock));

        // when
        List<IpBlockResponse> blockedIps = adminService.getBlockedIps();

        // then
        assertThat(blockedIps).hasSize(1);
        assertThat(blockedIps.get(0).getIpAddress()).isEqualTo("127.0.0.1");
    }

    @Test
    @DisplayName("IP 차단 여부 확인 성공")
    void isIpBlocked_success() {
        // given
        String ipAddress = "127.0.0.1";
        com.weedrice.whiteboard.domain.admin.entity.IpBlock ipBlock = com.weedrice.whiteboard.domain.admin.entity.IpBlock.builder()
                .ipAddress(ipAddress)
                .reason("Test")
                .build();
        when(ipBlockRepository.findByIpAddressAndEndDateAfterOrEndDateIsNull(eq(ipAddress), any(java.time.LocalDateTime.class)))
                .thenReturn(Optional.of(ipBlock));

        // when
        boolean isBlocked = adminService.isIpBlocked(ipAddress);

        // then
        assertThat(isBlocked).isTrue();
    }

    @Test
    @DisplayName("대시보드 통계 조회 성공")
    void getDashboardStats_success() {
        // given
        when(postRepository.count()).thenReturn(100L);
        when(reportRepository.countByStatus("PENDING")).thenReturn(5L);
        when(userRepository.count()).thenReturn(50L);
        when(userRepository.countByLastLoginAtAfter(any(java.time.LocalDateTime.class))).thenReturn(10L);

        // when
        com.weedrice.whiteboard.domain.admin.dto.DashboardStatsDto stats = adminService.getDashboardStats();

        // then
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
