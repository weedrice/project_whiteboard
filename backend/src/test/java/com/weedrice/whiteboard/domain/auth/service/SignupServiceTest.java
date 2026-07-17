package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.service.PasswordHistoryPolicy;
import com.weedrice.whiteboard.domain.user.service.SocialAccountLinkService;
import com.weedrice.whiteboard.domain.user.service.UserPrivilegeCleanupService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class SignupServiceTest {

    @Mock private UserRepository userRepository;
    @Mock private PointService pointService;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private SocialAccountLinkService socialAccountLinkService;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private AccountUniquenessPolicy accountUniquenessPolicy;
    @Mock private GlobalConfigService globalConfigService;
    @Mock private EntityManager entityManager;
    @Mock private RefreshTokenLifecycleService refreshTokenLifecycleService;
    @Mock private UserPrivilegeCleanupService userPrivilegeCleanupService;
    @Mock private PasswordHistoryPolicy passwordHistoryPolicy;
    @Mock private AuthAccountEligibilityPolicy authAccountEligibilityPolicy;
    @Mock private OAuthSignupTicketService oAuthSignupTicketService;

    @InjectMocks
    private SignupService signupService;

    @Test
    @DisplayName("재가입 사전 조회는 계정 상태와 무관한 비식별 응답을 반환한다")
    void checkEmailForReregister_returnsGenericResponseWithoutAccountLookup() {
        for (String email : new String[]{"active@example.com", "deleted@example.com", "unknown@example.com"}) {
            var response = signupService.checkEmailForReregister(email);

            assertThat(response.isCanReregister()).isFalse();
            assertThat(response.getMaskedLoginId()).isNull();
        }
        verifyNoInteractions(userRepository);
    }

    @Test
    @DisplayName("재가입 시 삭제 계정 재활성화 전에 활성 refresh token을 모두 회수한다")
    void signup_reregister_revokesRefreshTokensBeforeReactivation() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Rejoined User")
                .verificationTicket("ticket-1")
                .build();
        User deletedUser = User.builder()
                .loginId("testuser")
                .password("old-password")
                .email("test@example.com")
                .displayName("Deleted User")
                .build();
        ReflectionTestUtils.setField(deletedUser, "userId", 1L);
        ReflectionTestUtils.setField(deletedUser, "status", "DELETED");
        ReflectionTestUtils.setField(deletedUser, "deletedAt", LocalDateTime.now().minusDays(1));

        when(accountUniquenessPolicy.findReregisterableSignupUser(request.getEmail())).thenReturn(Optional.of(deletedUser));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deletedUser));
        when(passwordHistoryPolicy.encode(request.getPassword())).thenReturn("encoded-new-password");
        when(userRepository.save(deletedUser)).thenReturn(deletedUser);

        SignupResponse response = signupService.signup(request);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(deletedUser.getStatus()).isEqualTo("ACTIVE");
        assertThat(deletedUser.getDeletedAt()).isNull();
        assertThat(deletedUser.getDisplayName()).isEqualTo("Rejoined User");
        assertThat(deletedUser.getPassword()).isEqualTo("encoded-new-password");
        var inOrder = inOrder(
                verificationCodeService,
                refreshTokenLifecycleService,
                userPrivilegeCleanupService,
                passwordHistoryPolicy,
                userRepository);
        inOrder.verify(verificationCodeService).consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
        inOrder.verify(passwordHistoryPolicy).validateNotRecentlyUsed(deletedUser, request.getPassword());
        inOrder.verify(refreshTokenLifecycleService).revokeActiveRefreshTokens(deletedUser);
        inOrder.verify(userPrivilegeCleanupService).removeOperationalPrivileges(deletedUser);
        inOrder.verify(userRepository).save(deletedUser);
        inOrder.verify(passwordHistoryPolicy).record(deletedUser, "encoded-new-password");
        verify(pointService, never()).addPoint(anyLong(),
                anyInt(),
                anyString(),
                anyLong(),
                anyString());
    }

    @Test
    @DisplayName("재가입 verification ticket 검증이 실패하면 refresh token을 회수하지 않는다")
    void signup_reregister_doesNotRevokeTokensWhenVerificationFails() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Rejoined User")
                .verificationTicket("ticket-1")
                .build();
        User deletedUser = User.builder()
                .loginId("testuser")
                .password("old-password")
                .email("test@example.com")
                .displayName("Deleted User")
                .build();
        ReflectionTestUtils.setField(deletedUser, "userId", 1L);
        ReflectionTestUtils.setField(deletedUser, "status", "DELETED");
        ReflectionTestUtils.setField(deletedUser, "deletedAt", LocalDateTime.now().minusDays(1));

        when(accountUniquenessPolicy.findReregisterableSignupUser(request.getEmail())).thenReturn(Optional.of(deletedUser));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deletedUser));
        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR))
                .when(verificationCodeService)
                .consumeVerificationTicket(
                        request.getEmail(),
                        VerificationPurpose.SIGNUP,
                        request.getVerificationTicket());

        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.VALIDATION_ERROR);

        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(deletedUser);
        verify(userPrivilegeCleanupService, never()).removeOperationalPrivileges(deletedUser);
        verify(userRepository, never()).save(deletedUser);
        verify(verificationCodeService).consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
    }

    @Test
    @DisplayName("재가입 비밀번호가 최근 이력이면 티켓 소비 후 계정 부수 효과 전에 거절한다")
    void signup_reregister_recentPasswordRejectsAfterTicketConsumeBeforeSideEffects() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Rejoined User")
                .verificationTicket("ticket-1")
                .build();
        User deletedUser = User.builder()
                .loginId("testuser")
                .password("old-password")
                .email("test@example.com")
                .displayName("Deleted User")
                .build();
        ReflectionTestUtils.setField(deletedUser, "userId", 1L);
        ReflectionTestUtils.setField(deletedUser, "status", "DELETED");
        ReflectionTestUtils.setField(deletedUser, "deletedAt", LocalDateTime.now().minusDays(1));

        when(accountUniquenessPolicy.findReregisterableSignupUser(request.getEmail())).thenReturn(Optional.of(deletedUser));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deletedUser));
        doThrow(new BusinessException(ErrorCode.PASSWORD_RECENTLY_USED))
                .when(passwordHistoryPolicy).validateNotRecentlyUsed(deletedUser, request.getPassword());

        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);

        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(deletedUser);
        verify(userPrivilegeCleanupService, never()).removeOperationalPrivileges(deletedUser);
        verify(userRepository, never()).save(deletedUser);
        verify(verificationCodeService).consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
    }

    @Test
    @DisplayName("재가입 시 기존 아이디와 다른 loginId는 거절한다")
    void signup_reregister_rejectsChangedLoginId() {
        SignupRequest request = SignupRequest.builder()
                .loginId("changeduser")
                .password("password123")
                .email("test@example.com")
                .displayName("Rejoined User")
                .verificationTicket("ticket-1")
                .build();
        User deletedUser = User.builder()
                .loginId("testuser")
                .password("old-password")
                .email("test@example.com")
                .displayName("Deleted User")
                .build();
        ReflectionTestUtils.setField(deletedUser, "userId", 1L);
        ReflectionTestUtils.setField(deletedUser, "status", "DELETED");
        ReflectionTestUtils.setField(deletedUser, "deletedAt", LocalDateTime.now().minusDays(1));

        when(accountUniquenessPolicy.findReregisterableSignupUser(request.getEmail())).thenReturn(Optional.of(deletedUser));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(deletedUser));

        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting("errorCode")
                .isEqualTo(ErrorCode.INVALID_INPUT_VALUE);

        verify(verificationCodeService, never()).consumeVerificationTicket(
                anyString(),
                eq(VerificationPurpose.SIGNUP),
                anyString());
        verify(refreshTokenLifecycleService, never()).revokeActiveRefreshTokens(deletedUser);
        verify(userPrivilegeCleanupService, never()).removeOperationalPrivileges(deletedUser);
        verify(userRepository, never()).save(deletedUser);
    }

    @Test
    @DisplayName("회원가입 시 provider 정보가 있으면 소셜 계정 링크 서비스를 호출한다")
    void signup_rejectsRawOAuthProviderIdentity() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .provider("google")
                .providerId("google-user-1")
                .build();
        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(oAuthSignupTicketService, never()).consume(anyString());
        verify(verificationCodeService, never()).consumeVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("OAuth registration ticket이 있으면 서버 저장 provider 정보로 소셜 계정을 링크한다")
    void signup_linksSocialAccountFromOAuthRegistrationTicket() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .oauthRegistrationTicket("oauth-ticket-1")
                .build();
        when(oAuthSignupTicketService.consume("oauth-ticket-1"))
                .thenReturn(new OAuthSignupTicketService.OAuthSignupTicket(
                        "test@example.com",
                        "OAuth User",
                        "google",
                        "google-user-1"));
        User savedUser = stubSuccessfulSignup(request);

        signupService.signup(request);

        verify(socialAccountLinkService).linkSocialAccount(eq(savedUser), eq("google"), eq("google-user-1"));
    }

    @Test
    @DisplayName("회원가입 시 provider와 providerId가 모두 없으면 소셜 계정 링크를 건너뛴다")
    void signup_skipsSocialAccountLinkWhenBothProviderFieldsAreNull() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .build();
        stubSuccessfulSignup(request);

        signupService.signup(request);

        verify(socialAccountLinkService, never()).linkSocialAccount(any(), any(), any());
    }

    @Test
    @DisplayName("회원가입 시 provider만 있으면 소셜 계정 링크 서비스가 검증한다")
    void signup_rejectsRawProviderOnly() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .provider("google")
                .build();
        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("회원가입 시 providerId만 있으면 소셜 계정 링크 서비스가 검증한다")
    void signup_rejectsRawProviderIdOnly() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .providerId("google-user-1")
                .build();
        assertThatThrownBy(() -> signupService.signup(request))
                .isInstanceOf(BusinessException.class)
                .extracting(exception -> ((BusinessException) exception).getErrorCode())
                .isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("회원가입은 이메일을 정규화해 조회, 티켓 소비, 저장에 사용한다")
    void signup_normalizesEmailBeforeLookupTicketConsumptionAndSave() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email(" Test@Example.COM ")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .build();
        User savedUser = User.builder()
                .loginId("testuser")
                .password("encoded-password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(savedUser, "userId", 10L);

        when(accountUniquenessPolicy.findReregisterableSignupUser("test@example.com")).thenReturn(Optional.empty());
        when(passwordHistoryPolicy.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(userSettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(globalConfigService.getConfig("POINT_SIGNUP_BONUS")).thenReturn("0");

        SignupResponse response = signupService.signup(request);

        assertThat(response.getEmail()).isEqualTo("test@example.com");
        verify(accountUniquenessPolicy).findReregisterableSignupUser("test@example.com");
        verify(accountUniquenessPolicy).validateLoginIdAvailable(request.getLoginId());
        verify(verificationCodeService).consumeVerificationTicket(
                "test@example.com",
                VerificationPurpose.SIGNUP,
                "ticket-1");
        verify(userRepository).saveAndFlush(argThat(user -> "test@example.com".equals(user.getEmail())));
    }

    @Test
    @DisplayName("회원가입 보너스 설정이 잘못되면 기본값으로 지급한다")
    void signup_invalidBonusConfig_usesDefaultBonus() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .build();
        User savedUser = User.builder()
                .loginId("testuser")
                .password("encoded-password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(savedUser, "userId", 10L);

        when(accountUniquenessPolicy.findReregisterableSignupUser(request.getEmail())).thenReturn(Optional.empty());
        when(passwordHistoryPolicy.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(userSettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(globalConfigService.getConfig("POINT_SIGNUP_BONUS")).thenReturn("invalid");

        signupService.signup(request);

        verify(pointService).addPoint(eq(10L), eq(500), eq("회원가입 축하 포인트"), eq(10L), eq("USER"));
    }

    @Test
    @DisplayName("회원가입 보너스 설정이 0이면 포인트를 지급하지 않는다")
    void signup_zeroBonusConfig_skipsPointGrant() {
        SignupRequest request = SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .build();
        User savedUser = User.builder()
                .loginId("testuser")
                .password("encoded-password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(savedUser, "userId", 10L);

        when(accountUniquenessPolicy.findReregisterableSignupUser(request.getEmail())).thenReturn(Optional.empty());
        when(passwordHistoryPolicy.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(userSettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(globalConfigService.getConfig("POINT_SIGNUP_BONUS")).thenReturn("0");

        signupService.signup(request);

        verify(pointService, never()).addPoint(
                anyLong(),
                anyInt(),
                anyString(),
                anyLong(),
                anyString());
    }

    private User stubSuccessfulSignup(SignupRequest request) {
        User savedUser = User.builder()
                .loginId(request.getLoginId())
                .password("encoded-password")
                .email(request.getEmail())
                .displayName(request.getDisplayName())
                .build();
        ReflectionTestUtils.setField(savedUser, "userId", 10L);

        when(accountUniquenessPolicy.findReregisterableSignupUser(request.getEmail())).thenReturn(Optional.empty());
        when(passwordHistoryPolicy.encode(request.getPassword())).thenReturn("encoded-password");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(savedUser);
        when(userSettingsRepository.save(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(globalConfigService.getConfig("POINT_SIGNUP_BONUS")).thenReturn("0");
        return savedUser;
    }
}
