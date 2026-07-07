package com.weedrice.whiteboard.domain.auth.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

class AuthEntityTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 7, 7, 12, 0);

    @Test
    @DisplayName("RefreshToken 만료 및 유효성 확인")
    void refreshTokenTest() {
        User user = User.builder().build();
        LocalDateTime future = NOW.plusHours(1);
        LocalDateTime past = NOW.minusHours(1);

        RefreshToken token = RefreshToken.builder()
                .user(user)
                .tokenHash("token")
                .expiresAt(future)
                .build();

        assertThat(token.isExpiredAt(NOW)).isFalse();
        assertThat(token.isValidAt(NOW)).isTrue();

        token.revoke();
        assertThat(token.getIsRevoked()).isTrue();
        assertThat(token.isValidAt(NOW)).isFalse();

        RefreshToken expiredToken = RefreshToken.builder()
                .expiresAt(past)
                .build();
        assertThat(expiredToken.isExpiredAt(NOW)).isTrue();
    }

    @Test
    @DisplayName("VerificationCode 검증 및 만료 확인")
    void verificationCodeTest() {
        LocalDateTime future = NOW.plusMinutes(5);
        VerificationCode code = VerificationCode.builder()
                .email("test@test.com")
                .purpose(VerificationPurpose.SIGNUP)
                .code("a".repeat(VerificationCode.CODE_HASH_LENGTH))
                .expiryDate(future)
                .build();

        assertThat(code.getIsVerified()).isFalse();
        assertThat(code.isExpiredAt(NOW)).isFalse();
        assertThat(code.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_PENDING);

        code.markSent();
        assertThat(code.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_SENT);
        code.issueVerificationTicket("ticket-1", NOW.plusMinutes(5));
        assertThat(code.getIsVerified()).isTrue();
        assertThat(code.getVerificationTicket()).isEqualTo("ticket-1");

        code.markFailed();
        assertThat(code.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_FAILED);
        code.clearVerification();
        assertThat(code.getIsVerified()).isFalse();
    }

    @Test
    @DisplayName("PasswordResetToken 사용 및 만료 확인")
    void passwordResetTokenTest() {
        User user = User.builder().build();
        LocalDateTime future = NOW.plusMinutes(30);
        
        PasswordResetToken token = PasswordResetToken.builder()
                .token("reset-token")
                .user(user)
                .expiryDate(future)
                .build();

        assertThat(token.getIsUsed()).isFalse();
        assertThat(token.isExpiredAt(NOW)).isFalse();
        assertThat(token.getDeliveryStatus()).isEqualTo(PasswordResetToken.DELIVERY_STATUS_PENDING);

        token.markSent();
        assertThat(token.getDeliveryStatus()).isEqualTo(PasswordResetToken.DELIVERY_STATUS_SENT);
        token.useToken();
        assertThat(token.getIsUsed()).isTrue();

        token.markFailed();
        assertThat(token.getDeliveryStatus()).isEqualTo(PasswordResetToken.DELIVERY_STATUS_FAILED);
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
