package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthPasswordResetMailFlowTest {

    @Mock private UserRepository userRepository;
    @Mock private UserPointRepository userPointRepository;
    @Mock private PointService pointService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManagerBuilder authenticationManagerBuilder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private SocialAccountRepository socialAccountRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private GlobalConfigService globalConfigService;
    @Mock private TransactionTemplate transactionTemplate;

    @InjectMocks
    private AuthService authService;

    private final AtomicLong tokenIdSequence = new AtomicLong(1L);
    private final Map<Long, PasswordResetToken> passwordResetTokens = new HashMap<>();
    private User user;

    @BeforeEach
    void setUp() {
        user = User.builder()
                .loginId("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(authService, "passwordResetFrontendUrl", "http://localhost:5173/reset-password?token=");

        doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        doAnswer(invocation -> {
            PasswordResetToken passwordResetToken = invocation.getArgument(0);
            if (passwordResetToken.getTokenId() == null) {
                ReflectionTestUtils.setField(passwordResetToken, "tokenId", tokenIdSequence.getAndIncrement());
            }
            if (passwordResetToken.getCreatedAt() == null) {
                ReflectionTestUtils.setField(passwordResetToken, "createdAt", LocalDateTime.now());
            }
            passwordResetTokens.put(passwordResetToken.getTokenId(), passwordResetToken);
            return passwordResetToken;
        }).when(passwordResetTokenRepository).save(any(PasswordResetToken.class));

        when(passwordResetTokenRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(passwordResetTokens.get(invocation.getArgument(0))));
        when(passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user)).thenAnswer(invocation ->
                passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> passwordResetToken.getUser().equals(user))
                        .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                        .toList());
        when(passwordResetTokenRepository.findByToken(anyString())).thenAnswer(invocation ->
                passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> invocation.getArgument(0).equals(passwordResetToken.getToken()))
                        .findFirst());
    }

    @Test
    @DisplayName("비밀번호 재설정 메일 발송 성공 시 새 토큰은 SENT가 되고 이전 SENT 토큰은 무효화된다")
    void sendPasswordResetLinkByEmail_success_invalidatesPreviousSentToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        PasswordResetToken previousToken = PasswordResetToken.builder()
                .token("previous")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(previousToken, "tokenId", 100L);
        ReflectionTestUtils.setField(previousToken, "createdAt", LocalDateTime.now().minusMinutes(5));
        previousToken.markSent();
        passwordResetTokens.put(100L, previousToken);

        authService.sendPasswordResetLinkByEmail("test@example.com");

        assertThat(passwordResetTokens.values())
                .filteredOn(PasswordResetToken::isSent)
                .filteredOn(token -> !token.getIsUsed())
                .hasSize(1);
        assertThat(previousToken.getIsUsed()).isTrue();
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 메일 발송 실패 시 새 토큰은 FAILED가 되고 기존 SENT 토큰은 유지된다")
    void sendPasswordResetLinkByEmail_failure_keepsPreviousSentToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        PasswordResetToken previousToken = PasswordResetToken.builder()
                .token("previous")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(previousToken, "tokenId", 100L);
        ReflectionTestUtils.setField(previousToken, "createdAt", LocalDateTime.now().minusMinutes(5));
        previousToken.markSent();
        passwordResetTokens.put(100L, previousToken);

        assertThatThrownBy(() -> authService.sendPasswordResetLinkByEmail("test@example.com"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);

        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_FAILED.equals(token.getDeliveryStatus()))
                .hasSize(1);
        assertThat(previousToken.getIsUsed()).isFalse();
    }

    @Test
    @DisplayName("메일 발송 후 SENT 승격이 실패하면 대체 SENT 토큰을 저장한다")
    void sendPasswordResetLinkByEmail_promoteFailure_savesReplacementSentToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        AtomicLong executionCount = new AtomicLong();
        doAnswer(invocation -> {
            long current = executionCount.incrementAndGet();
            Consumer<Object> consumer = invocation.getArgument(0);
            if (current == 2L) {
                throw new IllegalStateException("status update failed");
            }
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        authService.sendPasswordResetLinkByEmail("test@example.com");

        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_SENT.equals(token.getDeliveryStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("PENDING 비밀번호 재설정 토큰은 사용할 수 없다")
    void resetPasswordWithToken_rejectsPendingToken() {
        PasswordResetToken pendingToken = PasswordResetToken.builder()
                .token("hashed")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(pendingToken, "tokenId", 1L);
        ReflectionTestUtils.setField(pendingToken, "createdAt", LocalDateTime.now());

        when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(pendingToken));
        when(passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(java.util.List.of(pendingToken));

        assertThatThrownBy(() -> authService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }

    @Test
    @DisplayName("FAILED 비밀번호 재설정 토큰은 사용할 수 없다")
    void resetPasswordWithToken_rejectsFailedToken() {
        PasswordResetToken failedToken = PasswordResetToken.builder()
                .token("hashed")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(failedToken, "tokenId", 2L);
        ReflectionTestUtils.setField(failedToken, "createdAt", LocalDateTime.now());
        failedToken.markFailed();

        when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(failedToken));
        when(passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(java.util.List.of(failedToken));

        assertThatThrownBy(() -> authService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }

    @Test
    @DisplayName("최신 SENT 가 아닌 비밀번호 재설정 토큰은 사용할 수 없다")
    void resetPasswordWithToken_rejectsOlderSentToken() {
        PasswordResetToken olderSentToken = PasswordResetToken.builder()
                .token("older-hashed")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(olderSentToken, "tokenId", 3L);
        ReflectionTestUtils.setField(olderSentToken, "createdAt", LocalDateTime.now().minusMinutes(1));
        olderSentToken.markSent();

        PasswordResetToken latestSentToken = PasswordResetToken.builder()
                .token("latest-hashed")
                .user(user)
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(latestSentToken, "tokenId", 4L);
        ReflectionTestUtils.setField(latestSentToken, "createdAt", LocalDateTime.now());
        latestSentToken.markSent();

        when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(olderSentToken));
        when(passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user))
                .thenReturn(java.util.List.of(latestSentToken, olderSentToken));

        assertThatThrownBy(() -> authService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }
}
