package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.within;

class UserEntityTest {

    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
    }

    @Test
    @DisplayName("사용자 삭제 처리")
    void delete_setsStatusAndDeletedAt() {
        // when
        user.delete();

        // then
        assertThat(user.getStatus()).isEqualTo("DELETED");
        assertThat(user.getDeletedAt()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("사용자 정지 처리")
    void suspend_setsStatus() {
        // when
        user.suspend();

        // then
        assertThat(user.getStatus()).isEqualTo("SUSPENDED");
    }

    @Test
    @DisplayName("사용자 활성화 처리")
    void activate_setsStatus() {
        // given
        user.suspend(); // Start from a non-active state

        // when
        user.activate();

        // then
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 부여")
    void grantSuperAdminRole_setsFlag() {
        // when
        user.grantSuperAdminRole();

        // then
        assertThat(user.getIsSuperAdmin()).isTrue();
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 해제")
    void revokeSuperAdminRole_setsFlag() {
        // given
        user.grantSuperAdminRole(); // Start with admin role

        // when
        user.revokeSuperAdminRole();

        // then
        assertThat(user.getIsSuperAdmin()).isFalse();
    }

    @Test
    @DisplayName("마지막 로그인 시간 업데이트")
    void updateLastLogin_setsLastLoginAt() {
        // when
        user.updateLastLogin();

        // then
        assertThat(user.getLastLoginAt()).isCloseTo(LocalDateTime.now(), within(1, ChronoUnit.SECONDS));
    }

    @Test
    @DisplayName("이메일 인증 처리")
    void verifyEmail_setsFlag() {
        // when
        user.verifyEmail();

        // then
        assertThat(user.getIsEmailVerified()).isTrue();
    }
}
