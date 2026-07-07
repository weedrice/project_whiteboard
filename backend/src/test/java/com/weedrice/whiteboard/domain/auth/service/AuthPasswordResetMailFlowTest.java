package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.PasswordHistoryPolicy;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentCaptor.forClass;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthPasswordResetMailFlowTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private EmailService emailService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private RefreshTokenLifecycleService refreshTokenLifecycleService;

    private PasswordResetService passwordResetService;

    private final AtomicLong tokenIdSequence = new AtomicLong(1L);
    private final Map<Long, PasswordResetToken> passwordResetTokens = new HashMap<>();
    private User user;

    @BeforeEach
    void setUp() {
        TokenHashService tokenHashService = new TokenHashService();
        PasswordResetTokenOrchestrationService passwordResetTokenOrchestrationService =
                new PasswordResetTokenOrchestrationService(
                        passwordResetTokenRepository,
                        userRepository,
                        new AuthMailDeliveryOrchestrationService(emailService),
                        transactionTemplate,
                        tokenHashService,
                        verificationCodeService,
                        FIXED_CLOCK);
        PasswordHistoryPolicy passwordHistoryPolicy =
                new PasswordHistoryPolicy(passwordHistoryRepository, passwordEncoder);
        passwordResetService = new PasswordResetService(
                userRepository, verificationCodeService, passwordResetTokenRepository,
                passwordHistoryPolicy, refreshTokenLifecycleService, tokenHashService,
                passwordResetTokenOrchestrationService, transactionTemplate, new AuthAccountEligibilityPolicy(), FIXED_CLOCK);

        user = User.builder()
                .loginId("testuser")
                .email("test@example.com")
                .password("encodedPassword")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(passwordResetService, "passwordResetFrontendUrl",
                "http://localhost:5173/reset-password?token=");

        doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        doAnswer(invocation -> {
            org.springframework.transaction.support.TransactionCallback<?> callback = invocation.getArgument(0);
            return callback.doInTransaction(null);
        }).when(transactionTemplate).execute(any());

        doAnswer(invocation -> {
            PasswordResetToken passwordResetToken = invocation.getArgument(0);
            if (passwordResetToken.getTokenId() == null) {
                ReflectionTestUtils.setField(passwordResetToken, "tokenId", tokenIdSequence.getAndIncrement());
            }
            if (passwordResetToken.getCreatedAt() == null) {
                ReflectionTestUtils.setField(passwordResetToken, "createdAt", FIXED_NOW);
            }
            passwordResetTokens.put(passwordResetToken.getTokenId(), passwordResetToken);
            return passwordResetToken;
        }).when(passwordResetTokenRepository).save(any(PasswordResetToken.class));

        when(passwordResetTokenRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(passwordResetTokens.get(invocation.getArgument(0))));
        when(passwordResetTokenRepository.findByIdForUpdate(any())).thenAnswer(invocation ->
                Optional.ofNullable(passwordResetTokens.get(invocation.getArgument(0))));
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.of(user));
        when(userRepository.findByEmailForUpdate(user.getEmail())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findLatestSentByUser(user)).thenAnswer(invocation ->
                passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> passwordResetToken.getUser().equals(user))
                        .filter(PasswordResetToken::isSent)
                        .sorted((left, right) -> {
                            int createdAtComparison = right.getCreatedAt().compareTo(left.getCreatedAt());
                            if (createdAtComparison != 0) {
                                return createdAtComparison;
                            }
                            return Long.compare(right.getTokenId(), left.getTokenId());
                        })
                        .findFirst());
        doAnswer(invocation -> {
            User targetUser = invocation.getArgument(0);
            Long excludeTokenId = invocation.getArgument(1);
            int[] invalidatedCount = {0};
            passwordResetTokens.values().stream()
                    .filter(passwordResetToken -> passwordResetToken.getUser().equals(targetUser))
                    .filter(PasswordResetToken::isSent)
                    .filter(passwordResetToken -> !passwordResetToken.getIsUsed())
                    .filter(passwordResetToken -> excludeTokenId == null
                            || !excludeTokenId.equals(passwordResetToken.getTokenId()))
                    .forEach(passwordResetToken -> {
                        passwordResetToken.invalidate();
                        invalidatedCount[0]++;
                    });
            return invalidatedCount[0];
        }).when(passwordResetTokenRepository).invalidatePreviousSentUnusedTokens(any(User.class), nullable(Long.class));
        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenAnswer(invocation ->
                passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> invocation.getArgument(0).equals(passwordResetToken.getToken()))
                        .findFirst());
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail invalidates previous sent token on success")
    void sendPasswordResetLinkByEmail_success_invalidatesPreviousSentToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        PasswordResetToken previousToken = PasswordResetToken.builder()
                .token("previous")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(previousToken, "tokenId", 100L);
        ReflectionTestUtils.setField(previousToken, "createdAt", FIXED_NOW.minusMinutes(5));
        previousToken.markSent();
        passwordResetTokens.put(100L, previousToken);

        passwordResetService.sendPasswordResetLinkByEmail("test@example.com", "ticket-1");

        assertThat(passwordResetTokens.values())
                .filteredOn(PasswordResetToken::isSent)
                .filteredOn(token -> !token.getIsUsed())
                .hasSize(1);
        assertThat(previousToken.getIsUsed()).isTrue();
        var inOrder = inOrder(verificationCodeService, emailService);
        inOrder.verify(verificationCodeService).validateVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-1");
        inOrder.verify(emailService).sendEmail(anyString(), anyString(), anyString());
        inOrder.verify(verificationCodeService).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-1");

        var lockOrder = inOrder(passwordResetTokenRepository, userRepository);
        lockOrder.verify(passwordResetTokenRepository).findByIdForUpdate(any());
        lockOrder.verify(passwordResetTokenRepository).invalidatePreviousSentUnusedTokens(any(User.class), any());
        lockOrder.verify(userRepository).findByIdForUpdate(user.getUserId());
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail keeps previous sent token when email send fails")
    void sendPasswordResetLinkByEmail_failure_keepsPreviousSentToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        PasswordResetToken previousToken = PasswordResetToken.builder()
                .token("previous")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(previousToken, "tokenId", 100L);
        ReflectionTestUtils.setField(previousToken, "createdAt", FIXED_NOW.minusMinutes(5));
        previousToken.markSent();
        passwordResetTokens.put(100L, previousToken);

        assertThatThrownBy(() -> passwordResetService.sendPasswordResetLinkByEmail("test@example.com", "ticket-2"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);

        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_FAILED.equals(token.getDeliveryStatus()))
                .hasSize(1);
        assertThat(previousToken.getIsUsed()).isFalse();
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(
                anyString(),
                any(),
                anyString());
    }

    @Test
    @DisplayName("sendPasswordResetLink rejects suspended users without sending email")
    void sendPasswordResetLink_suspendedUser_rejectsBeforeEmail() {
        ReflectionTestUtils.setField(user, "status", User.STATUS_SUSPENDED);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordResetService.sendPasswordResetLink("test@example.com", "ticket-suspended"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-suspended");
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail rejects suspended users without sending email")
    void sendPasswordResetLinkByEmail_suspendedUser_rejectsBeforeEmail() {
        ReflectionTestUtils.setField(user, "status", User.STATUS_SUSPENDED);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordResetService.sendPasswordResetLinkByEmail(
                "test@example.com",
                "ticket-suspended"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-suspended");
    }

    @Test
    @DisplayName("resetPasswordByCode rejects suspended users before consuming ticket")
    void resetPasswordByCode_suspendedUser_rejectsBeforeTicketConsumption() {
        ReflectionTestUtils.setField(user, "status", User.STATUS_SUSPENDED);
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordResetService.resetPasswordByCode(
                "test@example.com",
                "ticket-suspended",
                "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-suspended");
        verify(userRepository).findByEmailForUpdate("test@example.com");
        verify(passwordHistoryRepository, never()).save(any());
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(user);
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail recovers with a sent token after promotion failure")
    void sendPasswordResetLinkByEmail_promoteFailure_savesReplacementSentToken() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        AtomicLong executionCount = new AtomicLong();
        doAnswer(invocation -> {
            long current = executionCount.incrementAndGet();
            Consumer<Object> consumer = invocation.getArgument(0);
            if (current == 1L) {
                throw new IllegalStateException("status update failed");
            }
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        passwordResetService.sendPasswordResetLinkByEmail("test@example.com", "ticket-3");

        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_SENT.equals(token.getDeliveryStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail marks delivered token failed when promotion token lock is unavailable")
    void sendPasswordResetLinkByEmail_unavailablePromotionTokenLock_marksDeliveredTokenFailed() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findByIdForUpdate(any())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService
                .sendPasswordResetLinkByEmail("test@example.com", "ticket-missing-token"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);

        verify(emailService).sendEmail(anyString(), anyString(), anyString());
        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_FAILED.equals(token.getDeliveryStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail marks delivered token failed when promotion user is missing")
    void sendPasswordResetLinkByEmail_missingPromotionUser_marksDeliveredTokenFailed() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.empty());

        assertThatThrownBy(() -> passwordResetService
                .sendPasswordResetLinkByEmail("test@example.com", "ticket-missing-user"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        verify(emailService).sendEmail(anyString(), anyString(), anyString());
        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_FAILED.equals(token.getDeliveryStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail preserves delivered token when promotion retry also fails")
    void sendPasswordResetLinkByEmail_promoteRetryFailure_marksDeliveredTokenSent() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        PasswordResetToken previousToken = PasswordResetToken.builder()
                .token("previous")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(previousToken, "tokenId", 100L);
        ReflectionTestUtils.setField(previousToken, "createdAt", FIXED_NOW.minusMinutes(5));
        previousToken.markSent();
        passwordResetTokens.put(100L, previousToken);

        AtomicLong executionCount = new AtomicLong();
        doAnswer(invocation -> {
            long current = executionCount.incrementAndGet();
            Consumer<Object> consumer = invocation.getArgument(0);
            if (current == 1L || current == 2L) {
                throw new IllegalStateException("promotion failed");
            }
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        passwordResetService.sendPasswordResetLinkByEmail("test@example.com", "ticket-4");

        assertThat(previousToken.getIsUsed()).isFalse();
        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_SENT.equals(token.getDeliveryStatus()))
                .filteredOn(token -> !token.getIsUsed())
                .hasSize(2);

        var bodyCaptor = forClass(String.class);
        verify(emailService).sendEmail(anyString(), anyString(), bodyCaptor.capture());
        String deliveredRawToken = extractResetToken(bodyCaptor.getValue());
        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user)).thenReturn(List.of());
        when(passwordEncoder.matches("newPassword123!", "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode("newPassword123!")).thenReturn("encodedNewPassword");

        passwordResetService.resetPasswordWithToken(deliveredRawToken, "newPassword123!");

        assertThat(previousToken.getIsUsed()).isFalse();
        assertThat(passwordResetTokens.values())
                .filteredOn(PasswordResetToken::getIsUsed)
                .hasSize(1);
    }

    @Test
    @DisplayName("sendPasswordResetLinkByEmail preserves delivered token when ticket is consumed during promotion")
    void sendPasswordResetLinkByEmail_ticketConsumedDuringPromotion_marksDeliveredTokenSent() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));
        doThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(verificationCodeService)
                .consumeValidatedVerificationTicket(
                        "test@example.com",
                        VerificationPurpose.PASSWORD_RESET,
                        "ticket-consumed");

        passwordResetService.sendPasswordResetLinkByEmail("test@example.com", "ticket-consumed");

        verify(emailService).sendEmail(anyString(), anyString(), anyString());
        verify(verificationCodeService, times(3)).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-consumed");
        assertThat(passwordResetTokens.values())
                .filteredOn(token -> PasswordResetToken.DELIVERY_STATUS_SENT.equals(token.getDeliveryStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("resetPasswordWithToken rejects pending tokens")
    void resetPasswordWithToken_rejectsPendingToken() {
        PasswordResetToken pendingToken = PasswordResetToken.builder()
                .token("hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(pendingToken, "tokenId", 1L);
        ReflectionTestUtils.setField(pendingToken, "createdAt", FIXED_NOW);

        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.of(pendingToken));
        assertThatThrownBy(() -> passwordResetService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }

    @Test
    @DisplayName("resetPasswordWithToken rejects suspended users before using token")
    void resetPasswordWithToken_suspendedUser_rejectsBeforeUsingToken() {
        ReflectionTestUtils.setField(user, "status", User.STATUS_SUSPENDED);
        PasswordResetToken latestSentToken = PasswordResetToken.builder()
                .token("latest-hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(latestSentToken, "tokenId", 7L);
        ReflectionTestUtils.setField(latestSentToken, "createdAt", FIXED_NOW);
        latestSentToken.markSent();

        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.of(latestSentToken));
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordResetService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        assertThat(latestSentToken.getIsUsed()).isFalse();
        verify(passwordResetTokenRepository).findByTokenForUpdate(anyString());
        verify(passwordResetTokenRepository, never()).save(latestSentToken);
        verify(passwordHistoryRepository, never()).save(any());
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(user);
    }

    @Test
    @DisplayName("resetPasswordWithToken rejects deleted users before using token")
    void resetPasswordWithToken_deletedUser_rejectsBeforeUsingToken() {
        user.delete(java.time.LocalDateTime.of(2026, 7, 7, 12, 0));
        PasswordResetToken latestSentToken = PasswordResetToken.builder()
                .token("latest-hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(latestSentToken, "tokenId", 8L);
        ReflectionTestUtils.setField(latestSentToken, "createdAt", FIXED_NOW);
        latestSentToken.markSent();

        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.of(latestSentToken));
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> passwordResetService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_DELETED);

        assertThat(latestSentToken.getIsUsed()).isFalse();
        verify(passwordResetTokenRepository).findByTokenForUpdate(anyString());
        verify(passwordResetTokenRepository, never()).save(latestSentToken);
        verify(passwordHistoryRepository, never()).save(any());
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(user);
    }

    @Test
    @DisplayName("resetPasswordWithToken rejects failed tokens")
    void resetPasswordWithToken_rejectsFailedToken() {
        PasswordResetToken failedToken = PasswordResetToken.builder()
                .token("hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(failedToken, "tokenId", 2L);
        ReflectionTestUtils.setField(failedToken, "createdAt", FIXED_NOW);
        failedToken.markFailed();

        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.of(failedToken));
        assertThatThrownBy(() -> passwordResetService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }

    @Test
    @DisplayName("resetPasswordWithToken rejects older sent tokens")
    void resetPasswordWithToken_rejectsOlderSentToken() {
        PasswordResetToken olderSentToken = PasswordResetToken.builder()
                .token("older-hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(olderSentToken, "tokenId", 3L);
        ReflectionTestUtils.setField(olderSentToken, "createdAt", FIXED_NOW.minusMinutes(1));
        olderSentToken.markSent();

        PasswordResetToken latestSentToken = PasswordResetToken.builder()
                .token("latest-hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(latestSentToken, "tokenId", 4L);
        ReflectionTestUtils.setField(latestSentToken, "createdAt", FIXED_NOW);
        latestSentToken.markSent();

        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.of(olderSentToken));
        when(passwordResetTokenRepository.findLatestSentByUser(user)).thenReturn(Optional.of(latestSentToken));

        assertThatThrownBy(() -> passwordResetService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_PASSWORD_RESET_TOKEN);
    }

    @Test
    @DisplayName("resetPasswordWithToken stores used token before password reset side effects")
    void resetPasswordWithToken_marksTokenUsedAndSaves() {
        PasswordResetToken latestSentToken = PasswordResetToken.builder()
                .token("latest-hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(latestSentToken, "tokenId", 5L);
        ReflectionTestUtils.setField(latestSentToken, "createdAt", FIXED_NOW);
        latestSentToken.markSent();

        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.of(latestSentToken));
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findLatestSentByUser(user)).thenReturn(Optional.of(latestSentToken));
        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user)).thenReturn(List.of());
        when(passwordEncoder.matches("newPassword123!", "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode("newPassword123!")).thenReturn("encodedNewPassword");

        passwordResetService.resetPasswordWithToken("ignored", "newPassword123!");

        assertThat(latestSentToken.getIsUsed()).isTrue();
        var inOrder = inOrder(
                passwordResetTokenRepository,
                userRepository,
                passwordHistoryRepository,
                refreshTokenLifecycleService);
        inOrder.verify(passwordResetTokenRepository).findByTokenForUpdate(anyString());
        inOrder.verify(userRepository).findByIdForUpdate(user.getUserId());
        inOrder.verify(passwordResetTokenRepository).findLatestSentByUser(user);
        inOrder.verify(passwordResetTokenRepository).save(latestSentToken);
        inOrder.verify(passwordHistoryRepository).save(any());
        inOrder.verify(refreshTokenLifecycleService).revokeActiveRefreshTokens(user);
    }

    @Test
    @DisplayName("resetPasswordWithToken rejects latest used token as used")
    void resetPasswordWithToken_rejectsLatestUsedToken() {
        PasswordResetToken latestUsedToken = PasswordResetToken.builder()
                .token("latest-hashed")
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(latestUsedToken, "tokenId", 6L);
        ReflectionTestUtils.setField(latestUsedToken, "createdAt", FIXED_NOW);
        latestUsedToken.markSent();
        latestUsedToken.useToken();

        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenReturn(Optional.of(latestUsedToken));
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.of(user));
        when(passwordResetTokenRepository.findLatestSentByUser(user)).thenReturn(Optional.of(latestUsedToken));

        assertThatThrownBy(() -> passwordResetService.resetPasswordWithToken("ignored", "newPassword123!"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USED_PASSWORD_RESET_TOKEN);
    }

    private String extractResetToken(String emailBody) {
        String marker = "token=";
        int tokenStart = emailBody.indexOf(marker) + marker.length();
        int tokenEnd = emailBody.indexOf("\"", tokenStart);
        return emailBody.substring(tokenStart, tokenEnd);
    }
}
