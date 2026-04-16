package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
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
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SuperAdminService superAdminService;

    @Test
    @DisplayName("슈퍼 관리자 생성 성공")
    void createSuperAdmin_success() {
        String loginId = "testUser";
        User user = User.builder().loginId(loginId).build();
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse response = superAdminService.createSuperAdmin(loginId);

        assertThat(response.isSuperAdmin()).isTrue();
        assertThat(response.getLoginId()).isEqualTo(loginId);
    }

    @Test
    @DisplayName("슈퍼 관리자 해제 성공")
    void deactivateSuperAdmin_success() {
        String loginId = "testUser";
        User user = User.builder().loginId(loginId).build();
        user.grantSuperAdminRole();
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse response = superAdminService.deactivateSuperAdmin(loginId);

        assertThat(response.isSuperAdmin()).isFalse();
        assertThat(response.getLoginId()).isEqualTo(loginId);
    }

    @Test
    @DisplayName("슈퍼 관리자 목록 조회 성공")
    void getSuperAdmin_success() {
        User superAdmin1 = User.builder().loginId("super1").build();
        superAdmin1.grantSuperAdminRole();
        User superAdmin2 = User.builder().loginId("super2").build();
        superAdmin2.grantSuperAdminRole();
        when(userRepository.findByIsSuperAdminTrue()).thenReturn(List.of(superAdmin1, superAdmin2));

        List<SuperAdminResponse> superAdmins = superAdminService.getSuperAdmin();

        assertThat(superAdmins).hasSize(2);
        assertThat(superAdmins.get(0).getLoginId()).isEqualTo("super1");
        assertThat(superAdmins.get(1).getLoginId()).isEqualTo("super2");
    }
}
