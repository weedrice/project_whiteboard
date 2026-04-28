package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResult;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.PasswordHistory;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.service.SocialAccountLinkService;
import com.weedrice.whiteboard.domain.user.service.UserPrivilegeCleanupService;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import jakarta.persistence.EntityManager;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;
import org.springframework.dao.DataIntegrityViolationException;
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

import java.time.LocalDateTime;
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

    @Mock private UserRepository userRepository;
    @Mock private UserPointRepository userPointRepository;
    @Mock private UserSettingsRepository userSettingsRepository;
    @Mock private PointService pointService;
    @Mock private PasswordEncoder passwordEncoder;
    @Mock private JwtTokenProvider jwtTokenProvider;
    @Mock private AuthenticationManagerBuilder authenticationManagerBuilder;
    @Mock private RefreshTokenRepository refreshTokenRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private LoginHistoryAuditService loginHistoryAuditService;
    @Mock private SocialAccountLinkService socialAccountLinkService;
    @Mock private PasswordHistoryRepository passwordHistoryRepository;
    @Mock private VerificationCodeService verificationCodeService;
    @Mock private PasswordResetTokenRepository passwordResetTokenRepository;
    @Mock private EmailService emailService;
    @Mock private GlobalConfigService globalConfigService;
    @Mock private TransactionTemplate transactionTemplate;
    @Mock private SanctionService sanctionService;
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
        SessionTokenService sessionTokenService = new SessionTokenService(
                userRepository, userPointRepository, userSettingsRepository, jwtTokenProvider, authenticationManagerBuilder,
                refreshTokenRepository, loginHistoryRepository, sanctionService, tokenHashService,
                loginHistoryAuditService);
        PasswordResetTokenOrchestrationService passwordResetTokenOrchestrationService =
                new PasswordResetTokenOrchestrationService(
                        passwordResetTokenRepository,
                        userRepository,
                        new AuthMailDeliveryOrchestrationService(emailService),
                        transactionTemplate,
                        tokenHashService);
        PasswordResetService passwordResetService = new PasswordResetService(
                userRepository, passwordEncoder, verificationCodeService, passwordResetTokenRepository,
                passwordHistoryRepository, refreshTokenLifecycleService, tokenHashService,
                passwordResetTokenOrchestrationService);
        SignupService signupService = new SignupService(
                userRepository, pointService, passwordEncoder, userSettingsRepository,
                socialAccountLinkService, verificationCodeService, globalConfigService, entityManager,
                refreshTokenLifecycleService, userPrivilegeCleanupService);
        authService = new AuthService(signupService, sessionTokenService, passwordResetService);

        user = User.builder()
                .loginId("testuser")
                .password("encodedPassword")
                .email("test@example.com")
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
        when(userRepository.findByIdForUpdate(user.getUserId())).thenReturn(Optional.of(user));
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
        when(passwordResetTokenRepository.findByToken(anyString())).thenAnswer(invocation ->
                passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> invocation.getArgument(0).equals(passwordResetToken.getToken()))
                        .findFirst());
        when(passwordResetTokenRepository.findByTokenForUpdate(anyString())).thenAnswer(invocation ->
                passwordResetTokens.values().stream()
                        .filter(passwordResetToken -> invocation.getArgument(0).equals(passwordResetToken.getToken()))
                        .findFirst());
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
        inOrder.verify(verificationCodeService).validateVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
        inOrder.verify(pointService).addPoint(eq(1L), eq(500), anyString(), eq(1L), eq("USER"));
        inOrder.verify(verificationCodeService).consumeValidatedVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 로그인 ID")
    void signup_fail_duplicateLoginId() {
        SignupRequest request = signupRequest();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode().getCode()).isEqualTo("U002");
        verify(verificationCodeService, never()).validateVerificationTicket(anyString(), any(), anyString());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("회원가입 실패 - 중복 이메일")
    void signup_fail_duplicateEmail() {
        SignupRequest request = signupRequest();
        User activeUser = User.builder()
                .loginId("other")
                .email("test@example.com")
                .displayName("Other")
                .password("pwd")
                .build();
        ReflectionTestUtils.setField(activeUser, "status", "ACTIVE");
        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(activeUser));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode().getCode()).isEqualTo("U003");
        verify(verificationCodeService, never()).validateVerificationTicket(anyString(), any(), anyString());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("회원가입 실패 - 저장 시점 이메일 충돌")
    void signup_fail_duplicateEmail_onFlush() {
        SignupRequest request = signupRequest();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty(), Optional.of(user));
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate email"));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_EMAIL);
        verify(entityManager).clear();
        verify(verificationCodeService).validateVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("회원가입 실패 - 저장 시점 로그인 ID 충돌")
    void signup_fail_duplicateLoginId_onFlush() {
        SignupRequest request = signupRequest();

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty(), Optional.empty());
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false, true);
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
        when(userRepository.saveAndFlush(any(User.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate login"));

        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.DUPLICATE_LOGIN_ID);
        verify(entityManager).clear();
        verify(verificationCodeService).validateVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("재가입 시 deletedAt 초기화")
    void signup_reregister_clearsDeletedAt() {
        SignupRequest request = signupRequest();
        ReflectionTestUtils.setField(user, "status", "DELETED");
        ReflectionTestUtils.setField(user, "deletedAt", LocalDateTime.now().minusDays(1));

        when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(user));
        when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedRejoinedPassword");
        when(userRepository.save(user)).thenReturn(user);

        SignupResponse response = authService.signup(request);

        assertThat(response.getUserId()).isEqualTo(1L);
        assertThat(user.getStatus()).isEqualTo("ACTIVE");
        assertThat(user.getDeletedAt()).isNull();
        verify(verificationCodeService).validateVerificationTicket(
                request.getEmail(),
                VerificationPurpose.SIGNUP,
                request.getVerificationTicket());
        verify(verificationCodeService).consumeValidatedVerificationTicket(
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
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        UserSettings userSettings = new UserSettings(user);
        userSettings.updateSettings("dark", null, null, null);
        when(userSettingsRepository.findById(1L)).thenReturn(Optional.of(userSettings));
        when(sanctionService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(authentication)).thenReturn("accessToken");
        when(jwtTokenProvider.createRefreshToken(authentication)).thenReturn("refreshToken");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(1209600000L);

        LoginResult response = authService.login(request, httpServletRequest);

        assertThat(response.getAccessToken()).isEqualTo("accessToken");
        assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
        assertThat(response.getUser().getLoginId()).isEqualTo("testuser");
        assertThat(response.getUser().getTheme()).isEqualTo("DARK");
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
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        String expectedRefreshTokenHash = new TokenHashService().hashSha256("refreshToken");

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(userPointRepository.findById(1L)).thenReturn(Optional.empty());
        when(sanctionService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(authentication)).thenReturn("accessToken");
        when(jwtTokenProvider.createRefreshToken(authentication)).thenReturn("refreshToken");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(7_200_000L);
        LocalDateTime beforeLogin = LocalDateTime.now();

        LoginResult response = authService.login(request, httpServletRequest);

        LocalDateTime afterLogin = LocalDateTime.now();
        assertThat(response.getUser().getTheme()).isEqualTo("LIGHT");
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
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));
        when(sanctionService.isUserBanned(user)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request, httpServletRequest));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(jwtTokenProvider, never()).createAccessToken(any());
        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("USER_BANNED"));
    }

    @Test
    @DisplayName("Authentication failure is recorded before rethrow")
    void login_fail_whenAuthenticationFails_recordsFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));

        assertThrows(BadCredentialsException.class, () -> authService.login(request, httpServletRequest));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("AUTHENTICATION_FAILED"));
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    @DisplayName("Disabled authentication failure is recorded as inactive user")
    void login_fail_whenAuthenticationReportsDisabled_recordsInactiveFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new DisabledException("disabled"));

        assertThrows(DisabledException.class, () -> authService.login(request, httpServletRequest));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("USER_NOT_ACTIVE"));
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    @DisplayName("Locked authentication failure is recorded as banned user")
    void login_fail_whenAuthenticationReportsLocked_recordsBannedFailure() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new LockedException("locked"));

        assertThrows(LockedException.class, () -> authService.login(request, httpServletRequest));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("USER_BANNED"));
        verify(jwtTokenProvider, never()).createAccessToken(any());
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
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request, httpServletRequest));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND);
        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("USER_NOT_FOUND"));
        verify(jwtTokenProvider, never()).createAccessToken(any());
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
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);
        user.suspend();

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenReturn(authentication);
        when(userRepository.findById(1L)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.login(request, httpServletRequest));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.LOGIN_FAILED);
        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("USER_NOT_ACTIVE"));
        verify(sanctionService, never()).isUserBanned(any());
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    @DisplayName("Login failure recording failure does not change login failure response")
    void login_fail_whenFailureRecordingFails_preservesOriginalException() {
        LoginRequest request = new LoginRequest("testuser", "password123");
        AuthenticationManager authenticationManager = mock(AuthenticationManager.class);
        HttpServletRequest httpServletRequest = mock(HttpServletRequest.class);

        when(authenticationManagerBuilder.getObject()).thenReturn(authenticationManager);
        when(authenticationManager.authenticate(any(UsernamePasswordAuthenticationToken.class)))
                .thenThrow(new BadCredentialsException("bad credentials"));
        doThrow(new RuntimeException("audit unavailable"))
                .when(loginHistoryAuditService)
                .recordFailure(anyString(), nullable(String.class), nullable(String.class), anyString());

        assertThrows(BadCredentialsException.class, () -> authService.login(request, httpServletRequest));

        verify(loginHistoryAuditService).recordFailure(eq("testuser"), nullable(String.class),
                nullable(String.class), eq("AUTHENTICATION_FAILED"));
        verify(jwtTokenProvider, never()).createAccessToken(any());
    }

    @Test
    @DisplayName("refresh rotates token with persisted client metadata")
    void refresh_success_reusesClientMetadata() {
        RefreshToken storedRefreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash("hashed-old-token")
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();
        String expectedRefreshTokenHash = new TokenHashService().hashSha256("new-refresh-token");

        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(storedRefreshToken));
        when(sanctionService.isUserBanned(user)).thenReturn(false);
        when(jwtTokenProvider.createAccessToken(any(Authentication.class))).thenReturn("new-access-token");
        when(jwtTokenProvider.createRefreshToken(any(Authentication.class))).thenReturn("new-refresh-token");
        when(jwtTokenProvider.getAccessTokenValidityInMilliseconds()).thenReturn(1800L);
        when(jwtTokenProvider.getRefreshTokenValidityInMilliseconds()).thenReturn(1209600000L);

        var response = authService.refresh("old-refresh-token");

        assertThat(response.getAccessToken()).isEqualTo("new-access-token");
        assertThat(response.getRefreshToken()).isEqualTo("new-refresh-token");
        assertThat(storedRefreshToken.getIsRevoked()).isTrue();
        verify(refreshTokenRepository).save(storedRefreshToken);
        verify(refreshTokenRepository).save(argThat(rotatedToken ->
                rotatedToken != storedRefreshToken
                        && rotatedToken.getUser().equals(user)
                        && expectedRefreshTokenHash.equals(rotatedToken.getTokenHash())
                        && "127.0.0.1".equals(rotatedToken.getIpAddress())
                        && "browser".equals(rotatedToken.getDeviceInfo())
                        && !rotatedToken.getIsRevoked()));
    }

    @Test
    @DisplayName("refresh fails when user is banned")
    void refresh_fail_whenUserIsBanned() {
        RefreshToken refreshToken = RefreshToken.builder()
                .user(user)
                .tokenHash("hashed-old-token")
                .ipAddress("127.0.0.1")
                .deviceInfo("browser")
                .expiresAt(LocalDateTime.now().plusDays(7))
                .build();

        when(jwtTokenProvider.validateToken("old-refresh-token")).thenReturn(true);
        when(refreshTokenRepository.findByTokenHash(anyString())).thenReturn(Optional.of(refreshToken));
        when(sanctionService.isUserBanned(user)).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.refresh("old-refresh-token"));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_ACTIVE);
        verify(refreshTokenRepository).save(refreshToken);
        verify(jwtTokenProvider, never()).createAccessToken(any());
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
    @DisplayName("비밀번호 재설정 링크 메일 발송 성공")
    void sendPasswordResetLinkByEmail_success() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        authService.sendPasswordResetLinkByEmail(email, verificationTicket);

        verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(verificationCodeService).consumeValidatedVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(userRepository).findByEmail(email);
        verify(emailService).sendEmail(anyString(), anyString(), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 링크 메일 발송 실패 - 사용자 없음")
    void sendPasswordResetLinkByEmail_userNotFound() {
        String email = "unknown@example.com";
        String verificationTicket = "ticket-1";
        when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.sendPasswordResetLinkByEmail(email, verificationTicket));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND_BY_EMAIL);
        verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("비밀번호 재설정 링크 메일 발송 실패 - 탈퇴 사용자")
    void sendPasswordResetLinkByEmail_deletedUser() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        ReflectionTestUtils.setField(user, "status", "DELETED");
        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.sendPasswordResetLinkByEmail(email, verificationTicket));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_DELETED);
        verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
    }

    @Test
    @DisplayName("resetPasswordByCode는 verificationTicket을 먼저 소비한다")
    void resetPasswordByCode_success() {
        String email = "test@example.com";
        String verificationTicket = "ticket-1";
        String newPassword = "newPassword123!";

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
        when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
        when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

        authService.resetPasswordByCode(email, verificationTicket, newPassword);

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

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
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
                .expiryDate(LocalDateTime.now().plusMinutes(10))
                .build();
        ReflectionTestUtils.setField(passwordResetToken, "tokenId", 1L);
        ReflectionTestUtils.setField(passwordResetToken, "createdAt", LocalDateTime.now());
        passwordResetToken.markSent();
        passwordResetTokens.put(1L, passwordResetToken);

        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
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

        when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
        when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(recentHistory));
        when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
        when(passwordEncoder.matches(newPassword, "recentHash")).thenReturn(true);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> authService.resetPasswordByCode(email, verificationTicket, newPassword));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);
        verify(userRepository, never()).save(any());
        verify(verificationCodeService).validateVerificationTicket(
                email,
                VerificationPurpose.PASSWORD_RESET,
                verificationTicket);
        verify(verificationCodeService, never()).consumeValidatedVerificationTicket(anyString(), any(), anyString());
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
}

