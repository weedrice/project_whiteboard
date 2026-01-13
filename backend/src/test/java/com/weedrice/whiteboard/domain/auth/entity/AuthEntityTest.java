package com.weedrice.whiteboard.domain.auth.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEntityTest {

    @Test
    @DisplayName("RefreshToken 만료 및 유효성 확인")
    void refreshTokenTest() {
        User user = User.builder().build();
        LocalDateTime future = LocalDateTime.now().plusHours(1);
        LocalDateTime past = LocalDateTime.now().minusHours(1);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("token")
                .expiresAt(future)
                .build();

        assertThat(token.isExpired()).isFalse();
        assertThat(token.isValid()).isTrue();

        token.revoke();
        assertThat(token.getIsRevoked()).isTrue();
        assertThat(token.isValid()).isFalse();

        RefreshToken expiredToken = RefreshToken.builder()
                .expiresAt(past)
                .build();
        assertThat(expiredToken.isExpired()).isTrue();
    }

    @Test
    @DisplayName("VerificationCode 검증 및 만료 확인")
    void verificationCodeTest() {
        LocalDateTime future = LocalDateTime.now().plusMinutes(5);
        VerificationCode code = VerificationCode.builder()
                .email("test@test.com")
                .code("123456")
                .expiryDate(future)
                .build();

        assertThat(code.getIsVerified()).isFalse();
        assertThat(code.isExpired()).isFalse();

        code.verify();
        assertThat(code.getIsVerified()).isTrue();

        code.clearVerification();
        assertThat(code.getIsVerified()).isFalse();
    }

    @Test
    @DisplayName("PasswordResetToken 사용 및 만료 확인")
    void passwordResetTokenTest() {
        User user = User.builder().build();
        LocalDateTime future = LocalDateTime.now().plusMinutes(30);
        
        PasswordResetToken token = PasswordResetToken.builder()
                .token("reset-token")
                .user(user)
                .expiryDate(future)
                .build();

        assertThat(token.getIsUsed()).isFalse();
        assertThat(token.isExpired()).isFalse();

        token.useToken();
        assertThat(token.getIsUsed()).isTrue();
    }

    @Test
    @DisplayName("LoginHistory 생성 메서드")
    void loginHistoryTest() {
        User user = User.builder().build();
        
        LoginHistory success = LoginHistory.success(user, "127.0.0.1", "Chrome", "session-id");
        assertThat(success.getIsSuccess()).isTrue();
        assertThat(success.getUser()).isEqualTo(user);

        LoginHistory failure = LoginHistory.failure("testuser", "127.0.0.1", "Chrome", "Reason");
        assertThat(failure.getIsSuccess()).isFalse();
        assertThat(failure.getFailureReason()).isEqualTo("Reason");
    }
}
