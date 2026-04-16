package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.SuperAdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.SuperAdminUpdateResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.context.SecurityContextHolder;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SuperAdminServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private SuperAdminService superAdminService;

    @AfterEach
    void tearDown() {
        SecurityContextHolder.clearContext();
    }

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
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otherAdmin", "", List.of()));
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(userRepository.findByIsSuperAdminTrueAndDeletedAtIsNullForUpdate()).thenReturn(List.of(
                user,
                User.builder().loginId("otherAdmin").build()));
        when(userRepository.save(any(User.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SuperAdminUpdateResponse response = superAdminService.deactivateSuperAdmin(loginId);

        assertThat(response.isSuperAdmin()).isFalse();
        assertThat(response.getLoginId()).isEqualTo(loginId);
    }

    @Test
    @DisplayName("마지막 super admin 해제는 차단한다")
    void deactivateSuperAdmin_lastAdmin_forbidden() {
        String loginId = "testUser";
        User user = User.builder().loginId(loginId).build();
        user.grantSuperAdminRole();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otherAdmin", "", List.of()));
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(userRepository.findByIsSuperAdminTrueAndDeletedAtIsNullForUpdate()).thenReturn(List.of(user));

        assertThatThrownBy(() -> superAdminService.deactivateSuperAdmin(loginId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("자기 자신의 super admin 해제는 차단한다")
    void deactivateSuperAdmin_self_forbidden() {
        String loginId = "testUser";
        User user = User.builder().loginId(loginId).build();
        user.grantSuperAdminRole();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken(loginId, "", List.of()));
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> superAdminService.deactivateSuperAdmin(loginId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
    }

    @Test
    @DisplayName("슈퍼 관리자 목록 조회 성공")
    void getSuperAdmin_success() {
        User superAdmin1 = User.builder().loginId("super1").build();
        superAdmin1.grantSuperAdminRole();
        User superAdmin2 = User.builder().loginId("super2").build();
        superAdmin2.grantSuperAdminRole();
        when(userRepository.findByIsSuperAdminTrueAndDeletedAtIsNull()).thenReturn(List.of(superAdmin1, superAdmin2));

        List<SuperAdminResponse> superAdmins = superAdminService.getSuperAdmin();

        assertThat(superAdmins).hasSize(2);
        assertThat(superAdmins.get(0).getLoginId()).isEqualTo("super1");
        assertThat(superAdmins.get(1).getLoginId()).isEqualTo("super2");
    }

    @Test
    @DisplayName("super admin 해제 시 활성 계정 전용 조회를 사용한다")
    void deactivateSuperAdmin_usesActiveSuperAdminQuery() {
        String loginId = "testUser";
        User user = User.builder().loginId(loginId).build();
        user.grantSuperAdminRole();
        User deletedSuperAdmin = User.builder().loginId("deletedAdmin").build();
        deletedSuperAdmin.grantSuperAdminRole();
        deletedSuperAdmin.delete();
        SecurityContextHolder.getContext().setAuthentication(
                new UsernamePasswordAuthenticationToken("otherAdmin", "", List.of()));
        List<User> allSuperAdmins = List.of(deletedSuperAdmin, user);
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));
        when(userRepository.findByIsSuperAdminTrueAndDeletedAtIsNullForUpdate()).thenReturn(allSuperAdmins);

        assertThatThrownBy(() -> superAdminService.deactivateSuperAdmin(loginId))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode()).isEqualTo(ErrorCode.FORBIDDEN));
        verify(userRepository).findByIsSuperAdminTrueAndDeletedAtIsNullForUpdate();
        verify(userRepository, never()).findByIsSuperAdminTrue();
    }

    @Test
    @DisplayName("super admin 목록 조회는 활성 계정만 사용한다")
    void getSuperAdmin_usesActiveSuperAdminQuery() {
        User superAdmin = User.builder().loginId("super1").build();
        superAdmin.grantSuperAdminRole();
        when(userRepository.findByIsSuperAdminTrueAndDeletedAtIsNull()).thenReturn(List.of(superAdmin));

        List<SuperAdminResponse> superAdmins = superAdminService.getSuperAdmin();

        assertThat(superAdmins).hasSize(1);
        assertThat(superAdmins.get(0).getLoginId()).isEqualTo("super1");
        verify(userRepository).findByIsSuperAdminTrueAndDeletedAtIsNull();
    }
}
