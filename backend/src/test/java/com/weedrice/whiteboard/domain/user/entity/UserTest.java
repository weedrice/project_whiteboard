package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserTest {

    @Test
    @DisplayName("User 생성 빌더 테스트")
    void createUser() {
        User user = User.builder()
                .loginId("testuser")
                .email("test@example.com")
                .password("password")
                .displayName("Test User")
                .build();

        assertThat(user.getLoginId()).isEqualTo("testuser");
        assertThat(user.getEmail()).isEqualTo("test@example.com");
        assertThat(user.getDisplayName()).isEqualTo("Test User");
        assertThat(user.getIsSuperAdmin()).isFalse(); // 기본값 확인
        assertThat(user.getStatus()).isEqualTo("ACTIVE"); // 기본값 확인
    }

    @Test
    @DisplayName("마지막 로그인 시간 업데이트")
    void updateLastLogin() {
        User user = User.builder().build();
        user.updateLastLogin();
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("이메일 인증 처리")
    void verifyEmail() {
        User user = User.builder().build();
        user.verifyEmail();
        assertThat(user.getIsEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("프로필 정보 업데이트")
    void updateProfile() {
        User user = User.builder()
                .displayName("Old Name")
                .build();

        user.updateDisplayName("New Name");
        user.updateProfileImage("new.jpg");

        assertThat(user.getDisplayName()).isEqualTo("New Name");
        assertThat(user.getProfileImageUrl()).isEqualTo("new.jpg");
    }

    @Test
    @DisplayName("비밀번호 변경")
    void updatePassword() {
        User user = User.builder().password("oldPass").build();
        user.updatePassword("newPass");
        assertThat(user.getPassword()).isEqualTo("newPass");
    }

    @Test
    @DisplayName("회원 탈퇴 처리")
    void deleteUser() {
        User user = User.builder().build();
        user.delete();
        assertThat(user.getDeletedAt()).isNotNull();
        assertThat(user.getStatus()).isEqualTo("DELETED");
    }

    @Test
    @DisplayName("계정 정지 및 활성화")
    void suspendAndActivate() {
        User user = User.builder().build();
        
        user.suspend();
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
        
        user.activate();
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 부여 및 회수")
    void superAdminRole() {
        User user = User.builder().build();
        
        user.grantSuperAdminRole();
        assertThat(user.getIsSuperAdmin()).isTrue();
        
        user.revokeSuperAdminRole();
        assertThat(user.getIsSuperAdmin()).isFalse();
    }
}
