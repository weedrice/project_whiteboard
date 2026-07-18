package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.entity.VerificationCode;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.OAuthSignupTicketRepository;
import com.weedrice.whiteboard.domain.auth.OAuthSignupTicketProperties;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionPolicyService;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.service.CurrentUserSummaryAssembler;
import com.weedrice.whiteboard.domain.user.service.PasswordHistoryPolicy;
import com.weedrice.whiteboard.domain.user.service.SocialAccountLinkService;
import com.weedrice.whiteboard.domain.user.service.UserPrivilegeCleanupService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.ratelimit.RateLimitConfig;
import com.weedrice.whiteboard.global.ratelimit.RateLimitProperties;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.context.support.StaticMessageSource;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.BadCredentialsException;
import org.springframework.security.authentication.DisabledException;
import org.springframework.security.authentication.LockedException;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.atomic.AtomicLong;
import java.util.function.Consumer;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.nullable;
import static org.mockito.Mockito.doAnswer;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
class AuthServiceTest {

    private static final LocalDateTime FIXED_NOW = LocalDateTime.of(2026, 7, 7, 12, 0);
    private static final Clock FIXED_CLOCK = Clock.fixed(
            FIXED_NOW.toInstant(ZoneOffset.UTC),
            ZoneOffset.UTC);

    @Mock private UserRepository userRepository;
    @Mock private UserPointRepository userPointRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private PointService pointService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManagerBuilder authenticationManagerBuilder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginHistoryAuditService loginHistoryAuditService;
    @Mock private SocialAccountLinkService socialAccountLinkService;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private VerificationCodeRepository verificationCodeRepository;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private GlobalConfigService globalConfigService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private SanctionPolicyService sanctionPolicyService;
    @Mock private EntityManager entityManager;
    @Mock private RefreshTokenLifecycleService refreshTokenLifecycleService;
    @Mock private UserPrivilegeCleanupService userPrivilegeCleanupService;

    private AuthService authService;
    private User user;
    private final AtomicLong tokenIdSequence = new AtomicLong(1L);
    private final Map<Long, PasswordResetToken> passwordResetTokens = new ConcurrentHashMap<>();

    @BeforeEach
    void setUp() {
        TokenHashService tokenHashService = new TokenHashService();
        LoginAccountEligibilityService loginAccountEligibilityService =
                new LoginAccountEligibilityService(sanctionPolicyService);
        CurrentUserSummaryAssembler currentUserSummaryAssembler =
                new CurrentUserSummaryAssembler(userPointRepository, userSettingsRepository);
        SessionTokenService sessionTokenService = new SessionTokenService(
                userRepository, jwtTokenProvider, refreshTokenRepository, loginAccountEligibilityService, tokenHashService,
                transactionTemplate,
                mock(com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService.class),
                FIXED_CLOCK);
        PasswordResetTokenOrchestrationService passwordResetTokenOrchestrationService =
                new PasswordResetTokenOrchestrationService(
                        passwordResetTokenRepository,
                        userRepository,
                        new AuthMailDeliveryOrchestrationService(emailService),
                        transactionTemplate,
                        tokenHashService,
                        verificationCodeService,
                        verificationCodeRepository,
                        FIXED_CLOCK);
        PasswordHistoryPolicy passwordHistoryPolicy =
                new PasswordHistoryPolicy(passwordHistoryRepository, passwordEncoder);
        AuthAccountEligibilityPolicy authAccountEligibilityPolicy = new AuthAccountEligibilityPolicy();
        PasswordResetService passwordResetService = new PasswordResetService(
                userRepository, verificationCodeService, passwordResetTokenRepository, verificationCodeRepository,
                passwordHistoryPolicy, refreshTokenLifecycleService, tokenHashService,
                passwordResetTokenOrchestrationService, transactionTemplate, authAccountEligibilityPolicy, FIXED_CLOCK,
                new StaticMessageSource(), userSettingsRepository);
        SignupService signupService = new SignupService(
                userRepository, pointService, userSettingsRepository,
                socialAccountLinkService, verificationCodeService, globalConfigService,
                entityManager, refreshTokenLifecycleService, userPrivilegeCleanupService, passwordHistoryPolicy,
                authAccountEligibilityPolicy, new AccountUniquenessPolicy(userRepository),
                new OAuthSignupTicketService(
                        mock(OAuthSignupTicketRepository.class),
                        tokenHashService,
                        FIXED_CLOCK,
                        new OAuthSignupTicketProperties()));
        RateLimitProperties rateLimitProperties = new RateLimitProperties();
        rateLimitProperties.setAuthAccountLimit(1_000);
        LoginAccountRateLimiter loginAccountRateLimiter = new LoginAccountRateLimiter(
                new RateLimitConfig(rateLimitProperties),
                rateLimitProperties);
        authService = new AuthService(
                signupService,
                sessionTokenService,
                passwordResetService,
                userRepository,
                loginAccountEligibilityService,
                new LoginAuthenticator(authenticationManagerBuilder),
                new LoginAuditRecorder(loginHistoryAuditService, new io.micrometer.core.instrument.simple.SimpleMeterRegistry()),
                new LoginUserInfoAssembler(currentUserSummaryAssembler),
                loginAccountRateLimiter,
                FIXED_CLOCK);

        user = User.builder()
                .loginId("testuser")
                .password("encodedPassword")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(passwordResetService, "passwordResetFrontendUrl",
                "http://localhost:5173/reset-password#token=");
        VerificationCode passwordResetVerification = VerificationCode.builder()
                .email("test@example.com")
                .purpose(VerificationPurpose.PASSWORD_RESET)
                .code("hash")
                .expiryDate(FIXED_NOW.plusHours(1))
                .build();
        ReflectionTestUtils.setField(passwordResetVerification, "verificationId", 99L);
        passwordResetVerification.issueVerificationTicket("ticket-1", FIXED_NOW.plusMinutes(10));
        when(verificationCodeService.lockAndValidateVerificationTicket(
                anyString(), eq(VerificationPurpose.PASSWORD_RESET), anyString()))
                .thenReturn(passwordResetVerification);
        when(verificationCodeRepository.findByIdForUpdate(99L))
                .thenReturn(Optional.of(passwordResetVerification));

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
        when(passwordResetTokenRepository.findLatestSentByUser(any(User.class))).thenAnswer(invocation ->
                passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> passwordResetToken.getUser().equals(invocation.getArgument(0)))
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
        when(passwordResetTokenRepository.findByToken(anyString())).thenAnswer(invocation -> {
            Optional<PasswordResetToken> stored = passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> invocation.getArgument(0).equals(passwordResetToken.getToken()))
                        .findFirst();
            return stored.isPresent()
                    ? stored
                    : passwordResetTokenRepository.findByTokenForUpdate(invocation.getArgument(0));
        });
    }

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        SignupRequest request = signupRequest();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(globalConfigService.getConfig(anyString())).thenReturn("500");
        when(userRepository.saveAndFlush(any(User.class))).thenReturn(user);
        when(userSettingsRepository.save(any(UserSettings.class))).thenAnswer(invocation -> invocation.getArgument(0));

        SignupResponse response = authService.signup(request);

        assertThat(response.getLoginId()).isEqualTo(request.getLoginId());
        assertThat(response.getEmail()).isEqualTo(request.getEmail());
        assertThat(response.getDisplayName()).isEqualTo(request.getDisplayName());
        var inOrder = inOrder(verificationCodeService, pointService);
        inOrder.verify(verificationCodeService).consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
        inOrder.verify(pointService).addPoint(eq(1L), eq(500), anyString(), eq(1L), eq("USER"));
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 로그인 ID")
    void signup_fail_duplicateLoginId() {
        SignupRequest request = signupRequest();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode().getCode()).isEqualTo("U002");
        verify(verificationCodeService, never()).consumeVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 이메일")
    void signup_fail_duplicateEmail() {
        SignupRequest request = signupRequest();
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode().getCode()).isEqualTo("U003");
        verify(verificationCodeService, never()).consumeVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("회원가입 실패 - 저장 시점 이메일 충돌")
    void signup_fail_duplicateEmail_onFlush() {
        SignupRequest request = signupRequest();

        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.of(user));
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(entityManager).clear();
        verify(verificationCodeService).consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
    }

    @Test
    @DisplayName("회원가입 실패 - 저장 시점 로그인 ID 충돌")
    void signup_fail_duplicateLoginId_onFlush() {
        SignupRequest request = signupRequest();

        when(userRepository.findByEmail(request.getEmail()))
                .thenReturn(Optional.empty())
                .thenReturn(Optional.empty());
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false, true);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate login"));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
        verify(entityManager).clear();
        verify(verificationCodeService).consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
    }

    @Test
    @DisplayName("재가입 시 deletedAt 초기화")
    void signup_reregister_clearsDeletedAt() {
        SignupRequest request = signupRequest();
        ReflectionTestUtils.setField(user, "status", "DELETED");
        ReflectionTestUtils.setField(user, "deletedAt", FIXED_NOW.minusDays(1));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedRejoinedPassword");
        when(userRepository.save(user)).thenReturn(user);

        SignupResponse response = authService.signup(request);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getDeletedAt()).isNull();
        verify(verificationCodeService).consumeVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
    }

    @Test
    @DisplayName("로그인 성공")
    void login_success() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "encodedPassword",
                Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.emptyList());
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        UserSettings userSettings = new UserSettings(user);
        userSettings.updateSettings("dark", null, null, null);
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(userSettings));
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(any(Authentication.class), any(java.util.UUID.class))).thenReturn("accessToken");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("refreshToken");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(1209600000L);

        LoginResult response = authService.login(request, noMetadata());

        assertThat(response.getAccessToken()).isEqualTo("accessToken");
        assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
        assertThat(response.getUser().getLoginId()).isEqualTo("testuser");
        assertThat(response.getUser().getTheme()).isEqualTo("DARK");
        verify(loginHistoryAuditService).recordSuccess(1L, "testuser", null, null);
    }

    @Test
    @DisplayName("Login success response is preserved when success history recording fails")
    void login_success_whenSuccessHistoryRecordingFails_preservesLoginResponse() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "encodedPassword",
                Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.emptyList());
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(any(Authentication.class), any(java.util.UUID.class))).thenReturn("accessToken");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("refreshToken");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(1209600000L);
        doThrow(new RuntimeException("audit unavailable"))
                .when(loginHistoryAuditService)
                .recordSuccess(1L, "testuser", null, null);

        LoginResult response = authService.login(request, noMetadata());

        assertThat(response.getAccessToken()).isEqualTo("accessToken");
        assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
        assertThat(response.getUser().getLoginId()).isEqualTo("testuser");
        verify(refreshTokenRepository).save(any(RefreshToken.class));
    }

    @Test
    @DisplayName("Login trims persisted client metadata to column limits")
    void login_success_truncatesClientMetadataBeforePersistingRefreshToken() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "encodedPassword",
                Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.emptyList());
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        String longIpAddress = "1".repeat(60);
        String longUserAgent = "a".repeat(600);
        LoginClientMetadata metadata = new LoginClientMetadata(longIpAddress, longUserAgent);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(any(Authentication.class), any(java.util.UUID.class))).thenReturn("accessToken");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("refreshToken");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(1209600000L);

        authService.login(request, metadata);

        verify(refreshTokenRepository).save(argThat(token ->
                longIpAddress.substring(0, 45).equals(token.getIpAddress())
                        && longUserAgent.substring(0, 255).equals(token.getDeviceInfo())));
        verify(loginHistoryAuditService).recordSuccess(1L, "testuser", longIpAddress, longUserAgent);
    }

    @Test
    @DisplayName("refresh token DB expiresAt uses millisecond duration")
    void login_persistsRefreshTokenExpiresAtUsingMillisecondDuration() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "encodedPassword",
                Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.emptyList());
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        String expectedRefreshTokenHash = new TokenHashService().hashSha256("refreshToken");

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(any(Authentication.class), any(java.util.UUID.class))).thenReturn("accessToken");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("refreshToken");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(7_200_000L);
        LocalDateTime beforeLogin = FIXED_NOW;

        LoginResult response = authService.login(request, noMetadata());

        LocalDateTime afterLogin = FIXED_NOW;
        assertThat(response.getUser().getTheme()).isEqualTo("LIGHT");
        assertThat(user.getLastLoginAt()).isEqualTo(FIXED_NOW);
        verify(refreshTokenRepository).save(argThat(token ->
                expectedRefreshTokenHash.equals(token.getTokenHash())
                        && !token.getExpiresAt().isBefore(beforeLogin.plusHours(2))
                        && !token.getExpiresAt().isAfter(afterLogin.plusHours(2))));
    }

    @Test
    @DisplayName("BAN 사용자는 로그인할 수 없다")
    void login_fail_whenUserIsBanned() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "encodedPassword",
                Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.emptyList());
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request, noMetadata()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq(LoginAccountEligibilityService.FAILURE_REASON_USER_BANNED));
    }

    @Test
    @DisplayName("Authentication failure is recorded before rethrow")
    void login_fail_whenAuthenticationFails_recordsFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request, noMetadata()));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("AUTHENTICATION_FAILED"));
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("Disabled authentication failure is recorded as inactive user")
    void login_fail_whenAuthenticationReportsDisabled_recordsInactiveFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("disabled"));

        assertThrows(DisabledException.class, () -> authService.login(request, noMetadata()));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq(LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_ACTIVE));
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("Locked authentication failure is recorded as banned user")
    void login_fail_whenAuthenticationReportsLocked_recordsBannedFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("locked"));

        assertThrows(LockedException.class, () -> authService.login(request, noMetadata()));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq(LoginAccountEligibilityService.FAILURE_REASON_USER_BANNED));
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("Missing authenticated user is recorded before rethrow")
    void login_fail_whenAuthenticatedUserMissing_recordsFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "encodedPassword",
                Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.emptyList());
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request, noMetadata()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq(LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_FOUND));
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("Inactive user login failure is recorded")
    void login_fail_whenUserIsInactive_recordsFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        CustomUserDetails userDetails = new CustomUserDetails(1L, "testuser", "encodedPassword",
                Collections.emptyList());
        Authentication authentication = new UsernamePasswordAuthenticationToken(userDetails, null,
                Collections.emptyList());
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        user.suspend();

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request, noMetadata()));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq(LoginAccountEligibilityService.FAILURE_REASON_USER_NOT_ACTIVE));
        verify(sanctionPolicyService, never()).isUserBanned(any());
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("Login failure recording failure does not change login failure response")
    void login_fail_whenFailureRecordingFails_preservesOriginalException() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));
        doThrow(new RuntimeException("audit unavailable"))
                .when(loginHistoryAuditService)
                .recordFailure(anyString(), nullable(String.class), nullable(String.class), anyString());

        assertThrows(BadCredentialsException.class, () -> authService.login(request, noMetadata()));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("AUTHENTICATION_FAILED"));
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("refresh rotates token with persisted client metadata")
    void refresh_success_reusesClientMetadata() {
        String oldRefreshTokenHash = new TokenHashService().hashSha256("old-refresh-token");
        String expectedRefreshTokenHash = new TokenHashService().hashSha256("new-refresh-token");
        RefreshToken storedRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(oldRefreshTokenHash)
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(FIXED_NOW.plusDays(7))
                .build();

        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findRenewalCandidateByTokenHash(oldRefreshTokenHash))
                .thenReturn(Optional.of(renewalCandidate(10L, 1L)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)).thenReturn(Optional.of(storedRefreshToken));
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(any(Authentication.class), any(java.util.UUID.class))).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(1209600000L);

        var response = authService.refresh("old-refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(storedRefreshToken.getIsRevoked()).isTrue();
        assertThat(user.getLastLoginAt()).isEqualTo(FIXED_NOW);
        var lockOrder = inOrder(refreshTokenRepository, userRepository);
        lockOrder.verify(refreshTokenRepository).findRenewalCandidateByTokenHash(oldRefreshTokenHash);
        lockOrder.verify(userRepository).findByIdForUpdate(1L);
        lockOrder.verify(refreshTokenRepository).findByTokenHash(oldRefreshTokenHash);
        verify(refreshTokenRepository).save(storedRefreshToken);
        verify(refreshTokenRepository).save(argThat(rotatedToken ->
                rotatedToken != storedRefreshToken
                        && rotatedToken.getUser().equals(user)
                        && expectedRefreshTokenHash.equals(rotatedToken.getTokenHash())
                        && storedRefreshToken.getSessionFamilyId().equals(rotatedToken.getSessionFamilyId())
                        && "127.0.0.1".equals(rotatedToken.getIpAddress())
                        && "browser".equals(rotatedToken.getDeviceInfo())
                        && !rotatedToken.getIsRevoked()));
    }

    @Test
    @DisplayName("refresh fails when user is banned")
    void refresh_fail_whenUserIsBanned() {
        String oldRefreshTokenHash = new TokenHashService().hashSha256("old-refresh-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(oldRefreshTokenHash)
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(FIXED_NOW.plusDays(7))
                .build();

        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findRenewalCandidateByTokenHash(oldRefreshTokenHash))
                .thenReturn(Optional.of(renewalCandidate(10L, 1L)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)).thenReturn(Optional.of(refreshToken));
        when(sanctionPolicyService.isUserBanned(user)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh("old-refresh-token"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        assertThat(refreshToken.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken);
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("refresh fails and revokes token when user is inactive")
    void refresh_fail_whenUserIsInactive_revokesToken() {
        user.suspend();
        String oldRefreshTokenHash = new TokenHashService().hashSha256("old-refresh-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(oldRefreshTokenHash)
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(FIXED_NOW.plusDays(7))
                .build();

        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findRenewalCandidateByTokenHash(oldRefreshTokenHash))
                .thenReturn(Optional.of(renewalCandidate(10L, 1L)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)).thenReturn(Optional.of(refreshToken));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh("old-refresh-token"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        assertThat(refreshToken.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken);
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("refresh fails and revokes token when active user has deleted timestamp")
    void refresh_fail_whenActiveUserHasDeletedAt_revokesToken() {
        ReflectionTestUtils.setField(user, "deletedAt", FIXED_NOW.minusDays(1));
        String oldRefreshTokenHash = new TokenHashService().hashSha256("old-refresh-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash(oldRefreshTokenHash)
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(FIXED_NOW.plusDays(7))
                .build();

        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findRenewalCandidateByTokenHash(oldRefreshTokenHash))
                .thenReturn(Optional.of(renewalCandidate(10L, 1L)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)).thenReturn(Optional.of(refreshToken));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh("old-refresh-token"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        assertThat(refreshToken.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).save(refreshToken);
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("refresh fails when locked token does not match renewal candidate hash")
    void refresh_fail_whenLockedTokenHashDoesNotMatchCandidate() {
        String oldRefreshTokenHash = new TokenHashService().hashSha256("old-refresh-token");
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash("different-token-hash")
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(FIXED_NOW.plusDays(7))
                .build();

        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findRenewalCandidateByTokenHash(oldRefreshTokenHash))
                .thenReturn(Optional.of(renewalCandidate(10L, 1L)));
        when(userRepository.findByIdForUpdate(1L)).thenReturn(Optional.of(user));
        when(refreshTokenRepository.findByTokenHash(oldRefreshTokenHash)).thenReturn(Optional.of(refreshToken));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh("old-refresh-token"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_REFRESH_TOKEN);
        verify(refreshTokenRepository, never()).save(any());
        verify(jwtTokenProvider, never()).createAccessToken(any(), any(java.util.UUID.class));
    }

    @Test
    @DisplayName("로그인 ID 찾기 성공")
    void findLoginId_success() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        var response = authService.findLoginId(email, verificationTicket);

        assertThat(response.getLoginId()).isEqualTo("testuser");
        var inOrder = inOrder(verificationCodeService, userRepository);
        inOrder.verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.FIND_ID,
                verificationTicket);
        inOrder.verify(userRepository).findByEmail(email);
        inOrder.verify(verificationCodeService).consumeValidatedVerificationTicket(
                email,
                VerificationPurpose.FIND_ID,
                verificationTicket);
    }

    @Test
    @DisplayName("로그인 ID 찾기는 이메일을 정규화해 티켓과 사용자 조회에 사용한다")
    void findLoginId_normalizesEmail() {
        String verificationTicket = "ticket-1";
        when(userRepository.findByEmail("test@example.com")).thenReturn(Optional.of(user));

        var response = authService.findLoginId(" Test@Example.COM ", verificationTicket);

        assertThat(response.getLoginId()).isEqualTo("testuser");
        verify(verificationCodeService).validateVerificationTicket(
                "test@example.com",
                VerificationPurpose.FIND_ID,
                verificationTicket);
        verify(userRepository).findByEmail("test@example.com");
        verify(verificationCodeService).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.FIND_ID,
                verificationTicket);
    }

    @Test
    @DisplayName("로그인 ID 찾기는 비활성 계정이면 ticket을 소비하지 않는다")
    void findLoginId_inactiveUser_rejectsBeforeTicketConsumption() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        ReflectionTestUtils.setField(user, "status", User.STATUS_SUSPENDED);
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(
                BusinessException.class,
                () -> authService.findLoginId(email, verificationTicket));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.FIND_ID,
                verificationTicket);
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(
                anyString(),
                eq(VerificationPurpose.FIND_ID),
                anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 링크 메일 발송 성공")
    void sendPasswordResetLinkByEmail_success() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";

        authService.sendPasswordResetLinkByEmail(email, verificationTicket);

        verify(verificationCodeService).lockAndValidateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(verificationCodeService).consumeValidatedVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(userRepository).findByEmailForUpdate(email);
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 링크 메일 발송 실패 - 사용자 없음")
    void sendPasswordResetLinkByEmail_userNotFound() {
        String email = "unknown@example.com";
        String verificationTicket = "ticket-1";
        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.sendPasswordResetLinkByEmail(email, verificationTicket));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND_BY_EMAIL);
        verify(verificationCodeService, never()).lockAndValidateVerificationTicket(
                anyString(), any(), anyString());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 링크는 이메일을 정규화해 티켓과 사용자 조회에 사용한다")
    void sendPasswordResetLinkByEmail_normalizesEmail() {
        String verificationTicket = "ticket-1";
        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));

        authService.sendPasswordResetLinkByEmail(" Test@Example.COM ", verificationTicket);

        verify(verificationCodeService).lockAndValidateVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(userRepository).findByEmailForUpdate("test@example.com");
        verify(verificationCodeService).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 링크 메일 발송 실패 - 탈퇴 사용자")
    void sendPasswordResetLinkByEmail_deletedUser() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        ReflectionTestUtils.setField(user, "status", "DELETED");

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.sendPasswordResetLinkByEmail(email, verificationTicket));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_DELETED);
        verify(verificationCodeService, never()).lockAndValidateVerificationTicket(
                anyString(), any(), anyString());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 링크 메일 발송 실패 - 검증 티켓이 유효하지 않음")
    void sendPasswordResetLinkByEmail_invalidTicket() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        doThrow(new BusinessException(ErrorCode.EMAIL_NOT_VERIFIED))
                .when(verificationCodeService)
                .lockAndValidateVerificationTicket(email, VerificationPurpose.PASSWORD_RESET, verificationTicket);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.sendPasswordResetLinkByEmail(email, verificationTicket));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.EMAIL_NOT_VERIFIED);
        verify(userRepository).findByEmailForUpdate(email);
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
        verify(emailService, never()).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("resetPasswordByCode는 티켓 검증 후 사용자를 잠그고 비밀번호를 변경한다")
    void resetPasswordByCode_success() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        String newPassword = "newPassword123!";

        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user)).thenReturn(Collections.emptyList());
        when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

        authService.resetPasswordByCode(email, verificationTicket, newPassword);

        var inOrder = inOrder(verificationCodeService, userRepository, passwordHistoryRepository);
        inOrder.verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        inOrder.verify(userRepository).findByEmailForUpdate(email);
        inOrder.verify(passwordHistoryRepository).findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user);
        verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(verificationCodeService).consumeValidatedVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(userRepository).save(user);
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
    }

    @Test
    @DisplayName("resetPasswordByCode는 활성 refresh token을 모두 만료시킨다")
    void resetPasswordByCode_revokesRefreshTokens() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        String newPassword = "newPassword123!";

        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user))
                .thenReturn(Collections.emptyList());
        when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

        authService.resetPasswordByCode(email, verificationTicket, newPassword);

        verify(refreshTokenLifecycleService).revokeActiveRefreshTokens(user);
    }

    @Test
    @DisplayName("resetPasswordWithToken은 히스토리를 남기고 세션을 만료시킨다")
    void resetPasswordWithToken_success() {
        String rawToken = "raw-token";
        String hashedToken = new TokenHashService().hashSha256(rawToken);
        String newPassword = "newPassword123!";
        PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                .token(hashedToken)
                .user(user)
                .expiryDate(FIXED_NOW.plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(passwordResetToken, "tokenId", 1L);
        ReflectionTestUtils.setField(passwordResetToken, "createdAt", FIXED_NOW);
        passwordResetToken.markSent();
        passwordResetTokens.put(1L, passwordResetToken);

        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user)).thenReturn(Collections.emptyList());
        when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

        authService.resetPasswordWithToken(rawToken, newPassword);

        assertThat(passwordResetToken.getIsUsed()).isTrue();
        verify(refreshTokenLifecycleService).revokeActiveRefreshTokens(user);
        verify(passwordHistoryRepository).save(any(PasswordHistory.class));
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("resetPasswordByCode는 최근 비밀번호 재사용을 거절한다")
    void resetPasswordByCode_recentlyUsed() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        String newPassword = "newPassword123!";
        PasswordHistory recentHistory = PasswordHistory.builder()
                .user(user)
                .passwordHash("recentHash")
                .build();

        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user)).thenReturn(List.of(recentHistory));
        when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "recentHash")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.resetPasswordByCode(email, verificationTicket, newPassword));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);
        verify(userRepository, never()).save(any());
        verify(userRepository).findByEmailForUpdate(email);
        verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("인증 코드 비밀번호 재설정은 이메일을 정규화해 티켓과 사용자 조회에 사용한다")
    void resetPasswordByCode_normalizesEmail() {
        String verificationTicket = "ticket-1";
        String newPassword = "newPassword123!";

        when(userRepository.findByEmailForUpdate("test@example.com")).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(user)).thenReturn(Collections.emptyList());
        when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

        authService.resetPasswordByCode(" Test@Example.COM ", verificationTicket, newPassword);

        verify(verificationCodeService).validateVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(userRepository).findByEmailForUpdate("test@example.com");
        verify(verificationCodeService).consumeValidatedVerificationTicket(
                "test@example.com",
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(userRepository).save(user);
    }

    @Test
    @DisplayName("resetPasswordByCode rejects when email is not found by locked lookup")
    void resetPasswordByCode_emailNotFoundForUpdate() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";

        when(userRepository.findByEmailForUpdate(email)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.resetPasswordByCode(email, verificationTicket, "newPassword123!"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(passwordHistoryRepository, never()).findTop4ByUserOrderByCreatedAtDescHistoryIdDesc(any(User.class));
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
        verify(userRepository, never()).save(any());
    }

    @Test
    @DisplayName("resetPasswordByCode는 잘못된 verificationTicket이면 실패한다")
    void resetPasswordByCode_invalidTicket() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";

        doThrow(new BusinessException(ErrorCode.VALIDATION_ERROR))
                .when(verificationCodeService)
                .validateVerificationTicket(email, VerificationPurpose.PASSWORD_RESET, verificationTicket);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.resetPasswordByCode(email, verificationTicket, "newPassword123!"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
        verify(userRepository, never()).findByEmail(anyString());
    }

    private SignupRequest signupRequest() {
        return SignupRequest.builder()
                .loginId("testuser")
                .password("password123")
                .email("test@example.com")
                .displayName("Test User")
                .verificationTicket("ticket-1")
                .build();
    }

    private RefreshTokenRepository.RefreshTokenRenewalCandidate renewalCandidate(Long tokenId, Long userId) {
        return new RefreshTokenRepository.RefreshTokenRenewalCandidate() {
            @Override
            public Long getTokenId() {
                return tokenId;
            }

            @Override
            public Long getUserId() {
                return userId;
            }

            @Override
            public java.util.UUID getSessionFamilyId() {
                return new java.util.UUID(0L, tokenId);
            }
        };
    }

    private LoginClientMetadata noMetadata() {
        return LoginClientMetadata.empty();
    }
}
