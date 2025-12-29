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
}
