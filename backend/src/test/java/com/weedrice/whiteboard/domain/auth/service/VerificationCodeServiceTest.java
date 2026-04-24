package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
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
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
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
            if (verificationCode.getCreatedAt() == null) {
                ReflectionTestUtils.setField(verificationCode, "createdAt", LocalDateTime.now());
            }
            verificationCodes.put(verificationCode.getVerificationId(), verificationCode);
            return verificationCode;
        }).when(verificationCodeRepository).save(any(VerificationCode.class));

        when(verificationCodeRepository.findById(any())).thenAnswer(invocation ->
                Optional.ofNullable(verificationCodes.get(invocation.getArgument(0))));
        when(verificationCodeRepository.findLatestSentByEmailAndPurpose(anyString(), anyString()))
                .thenAnswer(invocation -> findLatestSentCode(
                        invocation.getArgument(0),
                        invocation.getArgument(1)));
        when(verificationCodeRepository.findLatestSentByEmailAndPurposeForUpdate(anyString(), anyString()))
                .thenAnswer(invocation -> findLatestSentCode(
                        invocation.getArgument(0),
                        invocation.getArgument(1)));
        when(verificationCodeRepository.findByEmailAndPurposeAndVerificationTicket(anyString(), any(), anyString()))
                .thenAnswer(invocation -> verificationCodes.values().stream()
                        .filter(code -> invocation.getArgument(0).equals(code.getEmail()))
                        .filter(code -> invocation.getArgument(1) == code.getPurpose())
                        .filter(code -> invocation.getArgument(2).equals(code.getVerificationTicket()))
                        .findFirst());
        when(verificationCodeRepository.findAllByEmailAndPurpose(anyString(), any()))
                .thenAnswer(invocation -> verificationCodes.values().stream()
                        .filter(code -> invocation.getArgument(0).equals(code.getEmail()))
                        .filter(code -> invocation.getArgument(1) == code.getPurpose())
                        .toList());
        verificationCodeService = new VerificationCodeService(
                verificationCodeRepository,
                userRepository,
                new AuthMailDeliveryOrchestrationService(emailService),
                transactionTemplate);
    }

    @Test
    @DisplayName("인증 코드 발송 성공 시 SENT 상태로 저장한다")
    void sendVerificationCode_success_marksSent() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        verificationCodeService.sendVerificationCode("test@example.com", VerificationPurpose.SIGNUP, null);

        assertThat(verificationCodes.values()).hasSize(1);
        VerificationCode verificationCode = verificationCodes.values().iterator().next();
        assertThat(verificationCode.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_SENT);
        assertThat(verificationCode.getPurpose()).isEqualTo(VerificationPurpose.SIGNUP);
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("인증 코드 재발송 시 같은 목적의 이전 ticket을 무효화한다")
    void sendVerificationCode_invalidatesPreviousTicket() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        VerificationCode oldCode = createSentCode(100L, "test@example.com", VerificationPurpose.SIGNUP, "111111");
        oldCode.issueVerificationTicket("ticket-old", LocalDateTime.now().plusMinutes(5));
        verificationCodes.put(100L, oldCode);

        verificationCodeService.sendVerificationCode("test@example.com", VerificationPurpose.SIGNUP, null);

        assertThat(oldCode.getVerificationTicket()).isNull();
        assertThat(oldCode.getIsTicketConsumed()).isTrue();
        verify(verificationCodeRepository).saveAll(any());
    }

    @Test
    @DisplayName("인증 코드 발송 실패 시 FAILED 상태로 저장한다")
    void sendVerificationCode_failure_marksFailed() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        doThrow(new BusinessException(ErrorCode.EMAIL_SEND_FAILED))
                .when(emailService).sendEmail(anyString(), anyString(), anyString());

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "test@example.com",
                VerificationPurpose.SIGNUP,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_SEND_FAILED);

        assertThat(verificationCodes.values()).hasSize(1);
        VerificationCode verificationCode = verificationCodes.values().iterator().next();
        assertThat(verificationCode.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_FAILED);
    }

    @Test
    @DisplayName("목적별 최신 SENT 코드만 검증하고 verification ticket을 발급한다")
    void verifyCode_issuesPurposeScopedTicket() {
        User deletedUser = User.builder().loginId("old-user").build();
        ReflectionTestUtils.setField(deletedUser, "status", "DELETED");
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(deletedUser));

        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.SIGNUP, "123456");
        VerificationCode otherPurposeCode = createSentCode(
                2L,
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "654321");
        ReflectionTestUtils.setField(otherPurposeCode, "createdAt", LocalDateTime.now());

        verificationCodes.put(1L, sentCode);
        verificationCodes.put(2L, otherPurposeCode);

        VerifyCodeResponse response = verificationCodeService.verifyCode(
                "test@example.com",
                "123456",
                VerificationPurpose.SIGNUP);

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getVerificationTicket()).isNotBlank();
        assertThat(response.isReregister()).isTrue();
        assertThat(sentCode.getVerificationTicket()).isEqualTo(response.getVerificationTicket());
        assertThat(otherPurposeCode.getVerificationTicket()).isNull();
    }

    @Test
    @DisplayName("같은 코드로 재검증해도 새로운 ticket을 재발급하지 않는다")
    void verifyCode_reusesActiveTicket() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.FIND_ID, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().plusMinutes(5));
        verificationCodes.put(1L, sentCode);

        VerifyCodeResponse response = verificationCodeService.verifyCode(
                "test@example.com",
                "123456",
                VerificationPurpose.FIND_ID);

        assertThat(response.getVerificationTicket()).isEqualTo("ticket-1");
    }

    @Test
    @DisplayName("이미 소비된 코드로는 새 ticket을 발급할 수 없다")
    void verifyCode_rejectsConsumedTicketCodeReuse() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.PASSWORD_RESET, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().plusMinutes(5));
        sentCode.consumeVerificationTicket();
        verificationCodes.put(1L, sentCode);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(
                "test@example.com",
                "123456",
                VerificationPurpose.PASSWORD_RESET))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("유효한 verification ticket은 한 번만 소비된다")
    void consumeVerificationTicket_marksConsumed() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.FIND_ID, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().plusMinutes(5));
        verificationCodes.put(1L, sentCode);

        verificationCodeService.consumeVerificationTicket("test@example.com", VerificationPurpose.FIND_ID, "ticket-1");

        assertThat(sentCode.getIsTicketConsumed()).isTrue();
        assertThat(sentCode.getVerificationTicket()).isNull();

        assertThatThrownBy(() -> verificationCodeService.consumeVerificationTicket(
                "test@example.com",
                VerificationPurpose.FIND_ID,
                "ticket-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("verification ticket 검증은 ticket을 소비하지 않는다")
    void validateVerificationTicket_keepsTicketActive() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.PASSWORD_RESET, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().plusMinutes(5));
        verificationCodes.put(1L, sentCode);

        verificationCodeService.validateVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-1");

        assertThat(sentCode.getIsTicketConsumed()).isFalse();
        assertThat(sentCode.getVerificationTicket()).isEqualTo("ticket-1");
    }

    @Test
    @DisplayName("사전 검증된 verification ticket은 만료 시각이 지나도 소비할 수 있다")
    void consumeValidatedVerificationTicket_consumesTicketAfterExpiry() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.PASSWORD_RESET, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().minusMinutes(1));
        verificationCodes.put(1L, sentCode);

        verificationCodeService.consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-1");

        assertThat(sentCode.getIsTicketConsumed()).isTrue();
        assertThat(sentCode.getVerificationTicket()).isNull();
    }

    @Test
    @DisplayName("다른 목적의 ticket은 소비할 수 없다")
    void consumeVerificationTicket_rejectsOtherPurpose() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.SIGNUP, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().plusMinutes(5));
        verificationCodes.put(1L, sentCode);

        assertThatThrownBy(() -> verificationCodeService.consumeVerificationTicket(
                "test@example.com",
                VerificationPurpose.FIND_ID,
                "ticket-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    @Test
    @DisplayName("만료된 ticket은 소비할 수 없다")
    void consumeVerificationTicket_rejectsExpiredTicket() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.CHANGE_EMAIL, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().minusMinutes(1));
        verificationCodes.put(1L, sentCode);

        assertThatThrownBy(() -> verificationCodeService.consumeVerificationTicket(
                "test@example.com",
                VerificationPurpose.CHANGE_EMAIL,
                "ticket-1"))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
    }

    private Optional<VerificationCode> findLatestSentCode(String email, String purposeName) {
        return verificationCodes.values().stream()
                .filter(code -> email.equals(code.getEmail()))
                .filter(code -> purposeName.equals(code.getPurpose().name()))
                .filter(code -> code.getDeliveryStatus() == null
                        || VerificationCode.DELIVERY_STATUS_SENT.equals(code.getDeliveryStatus()))
                .sorted((left, right) -> right.getCreatedAt().compareTo(left.getCreatedAt()))
                .findFirst();
    }

    private VerificationCode createSentCode(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            String code) {
        VerificationCode sentCode = VerificationCode.builder()
                .email(email)
                .purpose(purpose)
                .code(code)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(sentCode, "verificationId", verificationId);
        ReflectionTestUtils.setField(sentCode, "createdAt", LocalDateTime.now().minusMinutes(1));
        sentCode.markSent();
        return sentCode;
    }
}
