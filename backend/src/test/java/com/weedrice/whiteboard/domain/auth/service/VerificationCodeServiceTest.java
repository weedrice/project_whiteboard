package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.quality.Strictness;
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
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCodeServiceTest {

    @Mock
    private VerificationCodeRepository verificationCodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private TransactionTemplate transactionTemplate;

    @InjectMocks
    private VerificationCodeService verificationCodeService;

    private final AtomicLong idSequence = new AtomicLong(1L);
    private final Map<Long, VerificationCode> verificationCodes = new HashMap<>();

    @BeforeEach
    void setUp() {
        doAnswer(invocation -> {
            Consumer<Object> consumer = invocation.getArgument(0);
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        doAnswer(invocation -> {
            VerificationCode verificationCode = invocation.getArgument(0);
            if (verificationCode.getVerificationId() == null) {
                ReflectionTestUtils.setField(verificationCode, "verificationId", idSequence.getAndIncrement());
            }
            verificationCodes.put(verificationCode.getVerificationId(), verificationCode);
            return verificationCode;
        }).when(verificationCodeRepository).save(any(VerificationCode.class));

        when(verificationCodeRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(verificationCodes.get(invocation.getArgument(0))));
        when(verificationCodeRepository.findByEmailOrderByCreatedAtDesc(anyString()))
                .thenAnswer(invocation -> verificationCodes.values().stream()
                        .filter(code -> invocation.getArgument(0).equals(code.getEmail()))
                        .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                        .toList());
    }

    @Test
    @DisplayName("인증 코드 발송 성공 시 SENT 상태로 저장한다")
    void sendVerificationCode_success_marksSent() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        verificationCodeService.sendVerificationCode("test@example.com", true, null);

        assertThat(verificationCodes.values()).hasSize(1);
        VerificationCode verificationCode = verificationCodes.values().iterator().next();
        assertThat(verificationCode.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_SENT);
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("인증 코드 발송 실패 시 FAILED 상태로 저장하고 예외를 다시 던진다")
    void sendVerificationCode_failure_marksFailed() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode("test@example.com", true, null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);

        assertThat(verificationCodes.values()).hasSize(1);
        VerificationCode verificationCode = verificationCodes.values().iterator().next();
        assertThat(verificationCode.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_FAILED);
    }

    @Test
    @DisplayName("메일 발송 후 SENT 승격이 실패하면 대체 SENT 레코드를 저장한다")
    void sendVerificationCode_promoteFailure_savesReplacementSentCode() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        final AtomicLong executionCount = new AtomicLong();
        doAnswer(invocation -> {
            long current = executionCount.incrementAndGet();
            Consumer<Object> consumer = invocation.getArgument(0);
            if (current == 2L) {
                throw new IllegalStateException("status update failed");
            }
            consumer.accept(null);
            return null;
        }).when(transactionTemplate).executeWithoutResult(any());

        verificationCodeService.sendVerificationCode("test@example.com", true, null);

        assertThat(verificationCodes.values())
                .filteredOn(code -> VerificationCode.DELIVERY_STATUS_SENT.equals(code.getDeliveryStatus()))
                .hasSize(1);
    }

    @Test
    @DisplayName("최신 요청이 FAILED여도 가장 최근 SENT 코드로 인증한다")
    void verifyCode_usesLatestSentCode() {
        User deletedUser = User.builder().loginId("old-user").build();
        ReflectionTestUtils.setField(deletedUser, "status", "DELETED");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(deletedUser));

        VerificationCode sentCode = VerificationCode.builder()
                .email("test@example.com")
                .code("123456")
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(sentCode, "verificationId", 1L);
        ReflectionTestUtils.setField(sentCode, "createdAt", LocalDateTime.now().minusMinutes(1));
        sentCode.markSent();
        verificationCodes.put(1L, sentCode);

        VerificationCode failedCode = VerificationCode.builder()
                .email("test@example.com")
                .code("999999")
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(failedCode, "verificationId", 2L);
        ReflectionTestUtils.setField(failedCode, "createdAt", LocalDateTime.now());
        failedCode.markFailed();
        verificationCodes.put(2L, failedCode);

        VerifyCodeResponse response = verificationCodeService.verifyCode("test@example.com", "123456");

        assertThat(response.isVerified()).isTrue();
        assertThat(response.isReregister()).isTrue();
        assertThat(sentCode.getIsVerified()).isTrue();
        assertThat(failedCode.getIsVerified()).isFalse();
    }

    @Test
    @DisplayName("isVerified는 가장 최근 SENT 코드만 기준으로 판단한다")
    void isVerified_ignoresFailedLatestAttempt() {
        VerificationCode sentCode = VerificationCode.builder()
                .email("test@example.com")
                .code("123456")
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(sentCode, "verificationId", 1L);
        ReflectionTestUtils.setField(sentCode, "createdAt", LocalDateTime.now().minusMinutes(1));
        sentCode.markSent();
        sentCode.verify();
        verificationCodes.put(1L, sentCode);

        VerificationCode failedCode = VerificationCode.builder()
                .email("test@example.com")
                .code("999999")
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(failedCode, "verificationId", 2L);
        ReflectionTestUtils.setField(failedCode, "createdAt", LocalDateTime.now());
        failedCode.markFailed();
        verificationCodes.put(2L, failedCode);

        assertThat(verificationCodeService.isVerified("test@example.com")).isTrue();
        verify(verificationCodeRepository, never()).findTopByEmailOrderByCreatedAtDesc(anyString());
    }

    @Test
    @DisplayName("clearVerificationStatus는 해당 이메일의 검증된 SENT 코드를 모두 초기화한다")
    void clearVerificationStatus_clearsAllVerifiedSentCodes() {
        VerificationCode latestSentCode = VerificationCode.builder()
                .email("test@example.com")
                .code("123456")
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(latestSentCode, "verificationId", 1L);
        ReflectionTestUtils.setField(latestSentCode, "createdAt", LocalDateTime.now());
        latestSentCode.markSent();
        latestSentCode.verify();
        verificationCodes.put(1L, latestSentCode);

        VerificationCode olderSentCode = VerificationCode.builder()
                .email("test@example.com")
                .code("654321")
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(olderSentCode, "verificationId", 2L);
        ReflectionTestUtils.setField(olderSentCode, "createdAt", LocalDateTime.now().minusMinutes(1));
        olderSentCode.markSent();
        olderSentCode.verify();
        verificationCodes.put(2L, olderSentCode);

        verificationCodeService.clearVerificationStatus("test@example.com");

        assertThat(latestSentCode.getIsVerified()).isFalse();
        assertThat(olderSentCode.getIsVerified()).isFalse();
    }
}
