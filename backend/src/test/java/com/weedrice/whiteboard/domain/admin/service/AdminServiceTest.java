package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.repository.IpBlockRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
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
        Long userId = 1L;
        String role = "ADMIN";
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(adminRepository.existsByUserAndRoleAndIsActive(user, role, "Y")).thenReturn(false);
        when(adminRepository.save(any(Admin.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        Admin createdAdmin = adminService.createAdmin(userId, null, role);

        // then
        assertThat(createdAdmin.getRole()).isEqualTo(role);
        assertThat(createdAdmin.getUser()).isEqualTo(user);
    }

    @Test
    @DisplayName("IP 차단 성공")
    void blockIp_success() {
        // given
        Long adminUserId = 1L;
        String ipAddress = "127.0.0.1";
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(user));
        when(adminRepository.findByUserAndIsActive(user, "Y")).thenReturn(Optional.of(admin));
        when(ipBlockRepository.findById(ipAddress)).thenReturn(Optional.empty());
        when(ipBlockRepository.save(any(IpBlock.class))).thenAnswer(invocation -> invocation.getArgument(0));

        // when
        IpBlock ipBlock = adminService.blockIp(adminUserId, ipAddress, "Test", null);

        // then
        assertThat(ipBlock.getIpAddress()).isEqualTo(ipAddress);
        verify(ipBlockRepository).save(any(IpBlock.class));
    }
}
