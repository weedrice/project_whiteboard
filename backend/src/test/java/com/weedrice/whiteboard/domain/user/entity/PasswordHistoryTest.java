package com.weedrice.whiteboard.domain.user.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PasswordHistoryTest {

    @Test
    @DisplayName("PasswordHistory 생성 성공")
    void createPasswordHistory_success() {
        // given
        User user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        String passwordHash = "encodedPassword";

        // when
        PasswordHistory passwordHistory = PasswordHistory.builder()
                .user(user)
                .passwordHash(passwordHash)
                .build();

        // then
        assertThat(passwordHistory.getUser()).isEqualTo(user);
        assertThat(passwordHistory.getPasswordHash()).isEqualTo(passwordHash);
    }
}
