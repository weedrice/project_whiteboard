package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class UserEntityAdditionalTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@test.com")
                .displayName("Test User")
                .build();
    }

    @Test
    @DisplayName("사용자 생성 시 초기값 확인")
    void createUser_initialValues() {
        // then
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getIsEmailVerified()).isFalse();
        assertThat(user.getIsSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("마지막 로그인 시간 업데이트")
    void updateLastLogin_success() {
        // when
        user.updateLastLogin();

        // then
        assertThat(user.getLastLoginAt()).isNotNull();
    }

    @Test
    @DisplayName("이메일 인증")
    void verifyEmail_success() {
        // when
        user.verifyEmail();

        // then
        assertThat(user.getIsEmailVerified()).isTrue();
    }

    @Test
    @DisplayName("닉네임 변경")
    void updateDisplayName_success() {
        // when
        user.updateDisplayName("New Name");

        // then
        assertThat(user.getDisplayName()).isEqualTo("New Name");
    }

    @Test
    @DisplayName("프로필 이미지 변경")
    void updateProfileImage_success() {
        // when
        user.updateProfileImage("https://example.com/image.jpg");

        // then
        assertThat(user.getProfileImageUrl()).isEqualTo("https://example.com/image.jpg");
    }

    @Test
    @DisplayName("비밀번호 변경")
    void updatePassword_success() {
        // when
        user.updatePassword("newPassword");

        // then
        assertThat(user.getPassword()).isEqualTo("newPassword");
    }

    @Test
    @DisplayName("사용자 삭제")
    void delete_success() {
        // when
        user.delete();

        // then
        assertThat(user.getStatus()).isEqualTo("DELETED");
        assertThat(user.getDeletedAt()).isNotNull();
    }

    @Test
    @DisplayName("사용자 정지")
    void suspend_success() {
        // when
        user.suspend();

        // then
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("사용자 활성화")
    void activate_success() {
        // given
        user.suspend();

        // when
        user.activate();

        // then
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 부여")
    void grantSuperAdminRole_success() {
        // when
        user.grantSuperAdminRole();

        // then
        assertThat(user.getIsSuperAdmin()).isTrue();
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 해제")
    void revokeSuperAdminRole_success() {
        // given
        user.grantSuperAdminRole();

        // when
        user.revokeSuperAdminRole();

        // then
        assertThat(user.getIsSuperAdmin()).isFalse();
    }
}
