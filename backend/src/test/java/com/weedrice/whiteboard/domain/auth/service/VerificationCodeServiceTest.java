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
import org.mockito.ArgumentCaptor;
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
import java.util.regex.Matcher;
import java.util.regex.Pattern;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class VerificationCodeServiceTest {

    private static final Pattern VERIFICATION_CODE_BODY_PATTERN = Pattern.compile(
            "<h3>(\\d{%d})</h3>".formatted(VerificationCode.LEGACY_PLAIN_CODE_LENGTH));

    @Mock
    private VerificationCodeRepository verificationCodeRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private EmailService emailService;
    @Mock
    private TransactionTemplate transactionTemplate;

    private VerificationCodeService verificationCodeService;
    private final TokenHashService tokenHashService = new TokenHashService();

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
        when(verificationCodeRepository.findLatestDeliveryAttemptCreatedAt(anyString(), anyString()))
                .thenAnswer(invocation -> findLatestDeliveryAttemptCreatedAt(
                        invocation.getArgument(0),
                        invocation.getArgument(1)));
        when(verificationCodeRepository.countDeliveryAttemptsSince(anyString(), anyString(), any(LocalDateTime.class)))
                .thenAnswer(invocation -> countDeliveryAttemptsSince(
                        invocation.getArgument(0),
                        invocation.getArgument(1),
                        invocation.getArgument(2)));
        when(verificationCodeRepository.findByEmailAndPurposeAndVerificationTicket(anyString(), any(), anyString()))
                .thenAnswer(invocation -> verificationCodes.values().stream()
                        .filter(code -> invocation.getArgument(0).equals(code.getEmail()))
                        .filter(code -> invocation.getArgument(1) == code.getPurpose())
                        .filter(code -> invocation.getArgument(2).equals(code.getVerificationTicket()))
                        .findFirst());
        when(verificationCodeRepository.findByEmailAndPurposeAndVerificationTicketWithoutLock(
                anyString(), any(), anyString()))
                .thenAnswer(invocation -> verificationCodes.values().stream()
                        .filter(code -> invocation.getArgument(0).equals(code.getEmail()))
                        .filter(code -> invocation.getArgument(1) == code.getPurpose())
                        .filter(code -> invocation.getArgument(2).equals(code.getVerificationTicket()))
                        .findFirst());
        when(verificationCodeRepository.invalidateActiveTickets(anyString(), any(), any(), any()))
                .thenAnswer(invocation -> {
                    String email = invocation.getArgument(0);
                    VerificationPurpose purpose = invocation.getArgument(1);
                    Long excludeVerificationId = invocation.getArgument(2);
                    int invalidatedCount = 0;
                    for (VerificationCode code : verificationCodes.values()) {
                        if (email.equals(code.getEmail())
                                && purpose == code.getPurpose()
                                && (excludeVerificationId == null
                                        || !excludeVerificationId.equals(code.getVerificationId()))
                                && code.hasActiveVerificationTicket()) {
                            code.invalidateVerificationTicket();
                            invalidatedCount++;
                        }
                    }
                    return invalidatedCount;
                });
        VerificationCodeDeliveryService verificationCodeDeliveryService = new VerificationCodeDeliveryService(
                verificationCodeRepository,
                new AuthMailDeliveryOrchestrationService(emailService),
                tokenHashService,
                transactionTemplate);
        VerificationTicketService verificationTicketService = new VerificationTicketService(
                verificationCodeRepository,
                tokenHashService,
                new VerifyCodeResponseAssembler(userRepository));
        verificationCodeService = new VerificationCodeService(
                new EmailEligibilityService(
                        new AccountUniquenessPolicy(userRepository),
                        new AuthEmailLookupPolicy(userRepository),
                        new AuthAccountEligibilityPolicy()),
                verificationCodeDeliveryService,
                verificationTicketService);
    }

    @Test
    @DisplayName("인증 코드 발송 성공 시 SENT 상태로 저장한다")
    void sendVerificationCode_success_marksSent() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());

        verificationCodeService.sendVerificationCode(" test@example.com ", VerificationPurpose.SIGNUP, null);

        assertThat(verificationCodes.values()).hasSize(1);
        VerificationCode verificationCode = verificationCodes.values().iterator().next();
        assertThat(verificationCode.getEmail()).isEqualTo("test@example.com");
        assertThat(verificationCode.getDeliveryStatus()).isEqualTo(VerificationCode.DELIVERY_STATUS_SENT);
        assertThat(verificationCode.getPurpose()).isEqualTo(VerificationPurpose.SIGNUP);
        ArgumentCaptor<String> subjectCaptor = ArgumentCaptor.forClass(String.class);
        ArgumentCaptor<String> bodyCaptor = ArgumentCaptor.forClass(String.class);
        verify(emailService).sendEmail(eq("test@example.com"), subjectCaptor.capture(), bodyCaptor.capture());
        assertThat(subjectCaptor.getValue()).isEqualTo("[NoviIs] 이메일 인증 코드");
        assertThat(bodyCaptor.getValue())
                .contains("<h1>이메일 인증 코드</h1>")
                .contains("아래 인증 코드를 입력하여 이메일 인증을 완료해 주세요.");
        String rawCode = extractVerificationCode(bodyCaptor.getValue());
        assertThat(verificationCode.getCode()).hasSize(VerificationCode.CODE_HASH_LENGTH);
        assertThat(verificationCode.getCode()).isEqualTo(tokenHashService.hashSha256(rawCode));
    }

    @Test
    void sendVerificationCode_rejectsNullPurposeBeforeDelivery() {
        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode("test@example.com", null, null))
                .isInstanceOfSatisfying(BusinessException.class, businessException -> {
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("purpose is required");
                });

        assertThat(verificationCodes).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    void verifyCode_rejectsNullPurposeBeforeLookup() {
        assertThatThrownBy(() -> verificationCodeService.verifyCode("test@example.com", "123456", null))
                .isInstanceOfSatisfying(BusinessException.class, businessException -> {
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("purpose is required");
                });

        verify(verificationCodeRepository, never()).findLatestSentByEmailAndPurposeForUpdate(anyString(), anyString());
    }

    @Test
    @DisplayName("인증 코드 발송은 같은 이메일과 목적의 60초 이내 재시도를 제한한다")
    void sendVerificationCode_rejectsCooldownAttempt() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        VerificationCode recentAttempt = createAttemptCode(
                100L,
                "test@example.com",
                VerificationPurpose.SIGNUP,
                VerificationCode.DELIVERY_STATUS_SENT,
                LocalDateTime.now().minusSeconds(30));
        verificationCodes.put(100L, recentAttempt);

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "test@example.com",
                VerificationPurpose.SIGNUP,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);

        assertThat(verificationCodes).hasSize(1);
        verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("인증 코드 발송은 같은 이메일과 목적의 시간당 5회 초과를 제한한다")
    void sendVerificationCode_rejectsHourlyAttemptLimit() {
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.empty());
        LocalDateTime now = LocalDateTime.now();
        verificationCodes.put(100L, createAttemptCode(
                100L,
                "test@example.com",
                VerificationPurpose.SIGNUP,
                VerificationCode.DELIVERY_STATUS_SENT,
                now.minusMinutes(10)));
        verificationCodes.put(101L, createAttemptCode(
                101L,
                "test@example.com",
                VerificationPurpose.SIGNUP,
                VerificationCode.DELIVERY_STATUS_FAILED,
                now.minusMinutes(20)));
        verificationCodes.put(102L, createAttemptCode(
                102L,
                "test@example.com",
                VerificationPurpose.SIGNUP,
                VerificationCode.DELIVERY_STATUS_PENDING,
                now.minusMinutes(30)));
        verificationCodes.put(103L, createAttemptCode(
                103L,
                "test@example.com",
                VerificationPurpose.SIGNUP,
                null,
                now.minusMinutes(40)));
        verificationCodes.put(104L, createAttemptCode(
                104L,
                "test@example.com",
                VerificationPurpose.SIGNUP,
                VerificationCode.DELIVERY_STATUS_SENT,
                now.minusMinutes(50)));

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "test@example.com",
                VerificationPurpose.SIGNUP,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.RATE_LIMIT_EXCEEDED);

        assertThat(verificationCodes).hasSize(5);
        verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("인증 코드 발송은 너무 긴 이메일을 저장/발송 전에 거부한다")
    void sendVerificationCode_rejectsTooLongEmailBeforeSaveAndSend() {
        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "a".repeat(101),
                VerificationPurpose.SIGNUP,
                null))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> assertThat(((BusinessException) ex).getErrorCode())
                        .isEqualTo(ErrorCode.VALIDATION_ERROR));

        verify(verificationCodeRepository, never()).save(any(VerificationCode.class));
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
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
        verify(verificationCodeRepository).invalidateActiveTickets(
                eq("test@example.com"),
                eq(VerificationPurpose.SIGNUP),
                any(Long.class),
                any(LocalDateTime.class));
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
    @DisplayName("SIGNUP verification rejects active unverified email like final signup")
    void sendVerificationCode_rejectsActiveUnverifiedSignupEmail() {
        User user = User.builder().email("test@example.com").build();
        ReflectionTestUtils.setField(user, "status", "ACTIVE");
        ReflectionTestUtils.setField(user, "isEmailVerified", false);
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "test@example.com",
                VerificationPurpose.SIGNUP,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("CHANGE_EMAIL verification rejects any other account email")
    void sendVerificationCode_rejectsOtherUserEmailForChangeEmail() {
        User other = User.builder().email("next@example.com").build();
        ReflectionTestUtils.setField(other, "userId", 2L);
        ReflectionTestUtils.setField(other, "status", "DELETED");
        ReflectionTestUtils.setField(other, "isEmailVerified", false);
        when(userRepository.findByEmail("next@example.com")).thenReturn(Optional.of(other));

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "next@example.com",
                VerificationPurpose.CHANGE_EMAIL,
                1L))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.DUPLICATE_EMAIL);
    }

    @Test
    @DisplayName("FIND_ID verification rejects missing email before sending")
    void sendVerificationCode_rejectsMissingFindIdEmailBeforeSending() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "missing@example.com",
                VerificationPurpose.FIND_ID,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND);

        assertThat(verificationCodes).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("FIND_ID verification rejects inactive account before sending")
    void sendVerificationCode_rejectsInactiveFindIdEmailBeforeSending() {
        User user = User.builder().email("suspended@example.com").build();
        ReflectionTestUtils.setField(user, "status", User.STATUS_SUSPENDED);
        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "suspended@example.com",
                VerificationPurpose.FIND_ID,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        assertThat(verificationCodes).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PASSWORD_RESET verification rejects deleted account before sending")
    void sendVerificationCode_rejectsDeletedPasswordResetEmailBeforeSending() {
        User user = User.builder().email("deleted@example.com").build();
        ReflectionTestUtils.setField(user, "status", User.STATUS_DELETED);
        when(userRepository.findByEmail("deleted@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "deleted@example.com",
                VerificationPurpose.PASSWORD_RESET,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_DELETED);

        assertThat(verificationCodes).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PASSWORD_RESET verification rejects account with deletedAt before sending")
    void sendVerificationCode_rejectsDeletedAtPasswordResetEmailBeforeSending() {
        User user = User.builder().email("deleted-at@example.com").build();
        ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now());
        when(userRepository.findByEmail("deleted-at@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "deleted-at@example.com",
                VerificationPurpose.PASSWORD_RESET,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_DELETED);

        assertThat(verificationCodes).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PASSWORD_RESET verification rejects inactive account before sending")
    void sendVerificationCode_rejectsInactivePasswordResetEmailBeforeSending() {
        User user = User.builder().email("suspended@example.com").build();
        ReflectionTestUtils.setField(user, "status", User.STATUS_SUSPENDED);
        when(userRepository.findByEmail("suspended@example.com")).thenReturn(Optional.of(user));

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "suspended@example.com",
                VerificationPurpose.PASSWORD_RESET,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_ACTIVE);

        assertThat(verificationCodes).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("PASSWORD_RESET verification rejects missing email with password reset error code")
    void sendVerificationCode_rejectsMissingPasswordResetEmailBeforeSending() {
        when(userRepository.findByEmail("missing@example.com")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> verificationCodeService.sendVerificationCode(
                "missing@example.com",
                VerificationPurpose.PASSWORD_RESET,
                null))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.USER_NOT_FOUND_BY_EMAIL);

        assertThat(verificationCodes).isEmpty();
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
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
                " test@example.com ",
                "123456",
                VerificationPurpose.SIGNUP);

        assertThat(response.isVerified()).isTrue();
        assertThat(response.getVerificationTicket()).isNotBlank();
        assertThat(response.isReregister()).isTrue();
        assertThat(sentCode.getVerificationTicket()).isEqualTo(response.getVerificationTicket());
        assertThat(otherPurposeCode.getVerificationTicket()).isNull();
    }

    @Test
    @DisplayName("전환 전 원문 인증 코드는 만료 전까지 검증할 수 있다")
    void verifyCode_acceptsLegacyPlainCodeDuringTransition() {
        VerificationCode sentCode = createLegacySentCode(1L, "test@example.com", VerificationPurpose.FIND_ID, "123456");
        verificationCodes.put(1L, sentCode);

        VerifyCodeResponse response = verificationCodeService.verifyCode(
                "test@example.com",
                "123456",
                VerificationPurpose.FIND_ID);

        assertThat(response.isVerified()).isTrue();
        assertThat(sentCode.getVerificationTicket()).isEqualTo(response.getVerificationTicket());
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
    @DisplayName("만료된 인증 코드는 활성 ticket이 있어도 거부한다")
    void verifyCode_reusesActiveTicketAfterCodeExpiry() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.FIND_ID, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().plusMinutes(5));
        ReflectionTestUtils.setField(sentCode, "expiryDate", LocalDateTime.now().minusMinutes(1));
        verificationCodes.put(1L, sentCode);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(
                "test@example.com",
                "123456",
                VerificationPurpose.FIND_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("만료된 인증 코드입니다.");
                });
    }

    @Test
    @DisplayName("활성 ticket 재조회도 잘못된 코드는 거부한다")
    void verifyCode_rejectsInvalidCodeWhenActiveTicketExists() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.FIND_ID, "123456");
        sentCode.issueVerificationTicket("ticket-1", LocalDateTime.now().plusMinutes(5));
        verificationCodes.put(1L, sentCode);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(
                "test@example.com",
                "000000",
                VerificationPurpose.FIND_ID))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("잘못된 인증 코드입니다.");
                });
    }

    @Test
    @DisplayName("만료된 인증 코드는 정상 메시지로 거부한다")
    void verifyCode_rejectsExpiredCodeWithReadableMessage() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.SIGNUP, "123456");
        ReflectionTestUtils.setField(sentCode, "expiryDate", LocalDateTime.now().minusMinutes(1));
        verificationCodes.put(1L, sentCode);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(
                "test@example.com",
                "123456",
                VerificationPurpose.SIGNUP))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("만료된 인증 코드입니다.");
                });
    }

    @Test
    @DisplayName("잘못된 인증 코드는 정상 메시지로 거부한다")
    void verifyCode_rejectsInvalidCodeWithReadableMessage() {
        VerificationCode sentCode = createSentCode(1L, "test@example.com", VerificationPurpose.SIGNUP, "123456");
        verificationCodes.put(1L, sentCode);

        assertThatThrownBy(() -> verificationCodeService.verifyCode(
                "test@example.com",
                "000000",
                VerificationPurpose.SIGNUP))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("잘못된 인증 코드입니다.");
                });
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
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                    assertThat(businessException.getMessage()).isEqualTo("이미 사용된 인증 코드입니다.");
                });
    }

    @Test
    @DisplayName("발송된 인증 코드가 없으면 정상 메시지로 거부한다")
    void verifyCode_missingSentCodeWithReadableMessage() {
        assertThatThrownBy(() -> verificationCodeService.verifyCode(
                "test@example.com",
                "123456",
                VerificationPurpose.SIGNUP))
                .isInstanceOf(BusinessException.class)
                .satisfies(ex -> {
                    BusinessException businessException = (BusinessException) ex;
                    assertThat(businessException.getErrorCode()).isEqualTo(ErrorCode.NOT_FOUND);
                    assertThat(businessException.getMessage())
                            .isEqualTo("인증 코드를 찾을 수 없습니다. 이메일을 변경했다면 다시 인증 코드를 발송해 주세요.");
                });
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
        verify(verificationCodeRepository).findByEmailAndPurposeAndVerificationTicketWithoutLock(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-1");
        verify(verificationCodeRepository, never()).findByEmailAndPurposeAndVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                "ticket-1");
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

    private Optional<LocalDateTime> findLatestDeliveryAttemptCreatedAt(String email, String purposeName) {
        return verificationCodes.values().stream()
                .filter(code -> email.equals(code.getEmail()))
                .filter(code -> purposeName.equals(code.getPurpose().name()))
                .filter(this::isDeliveryAttempt)
                .max((left, right) -> {
                    int createdAtCompare = left.getCreatedAt().compareTo(right.getCreatedAt());
                    if (createdAtCompare != 0) {
                        return createdAtCompare;
                    }
                    return Long.compare(left.getVerificationId(), right.getVerificationId());
                })
                .map(VerificationCode::getCreatedAt);
    }

    private long countDeliveryAttemptsSince(String email, String purposeName, LocalDateTime createdAtFrom) {
        return verificationCodes.values().stream()
                .filter(code -> email.equals(code.getEmail()))
                .filter(code -> purposeName.equals(code.getPurpose().name()))
                .filter(this::isDeliveryAttempt)
                .filter(code -> !code.getCreatedAt().isBefore(createdAtFrom))
                .count();
    }

    private boolean isDeliveryAttempt(VerificationCode code) {
        return code.getDeliveryStatus() == null
                || VerificationCode.DELIVERY_STATUS_PENDING.equals(code.getDeliveryStatus())
                || VerificationCode.DELIVERY_STATUS_SENT.equals(code.getDeliveryStatus())
                || VerificationCode.DELIVERY_STATUS_FAILED.equals(code.getDeliveryStatus());
    }

    private VerificationCode createSentCode(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            String code) {
        return createStoredCode(verificationId, email, purpose, tokenHashService.hashSha256(code));
    }

    private VerificationCode createLegacySentCode(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            String code) {
        return createStoredCode(verificationId, email, purpose, code);
    }

    private VerificationCode createStoredCode(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            String storedCode) {
        VerificationCode sentCode = VerificationCode.builder()
                .email(email)
                .purpose(purpose)
                .code(storedCode)
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(sentCode, "verificationId", verificationId);
        ReflectionTestUtils.setField(sentCode, "createdAt", LocalDateTime.now().minusMinutes(1));
        sentCode.markSent();
        return sentCode;
    }

    private VerificationCode createAttemptCode(
            Long verificationId,
            String email,
            VerificationPurpose purpose,
            String deliveryStatus,
            LocalDateTime createdAt) {
        VerificationCode verificationCode = VerificationCode.builder()
                .email(email)
                .purpose(purpose)
                .code(tokenHashService.hashSha256("123456"))
                .expiryDate(LocalDateTime.now().plusMinutes(5))
                .build();
        ReflectionTestUtils.setField(verificationCode, "verificationId", verificationId);
        ReflectionTestUtils.setField(verificationCode, "createdAt", createdAt);
        ReflectionTestUtils.setField(verificationCode, "deliveryStatus", deliveryStatus);
        return verificationCode;
    }

    private String extractVerificationCode(String body) {
        Matcher matcher = VERIFICATION_CODE_BODY_PATTERN.matcher(body);
        assertThat(matcher.find()).isTrue();
        return matcher.group(1);
    }
}
