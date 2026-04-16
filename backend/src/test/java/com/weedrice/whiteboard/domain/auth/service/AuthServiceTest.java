package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.dto.VerifyCodeResponse;
import com.weedrice.whiteboard.domain.auth.entity.PasswordResetToken;
import com.weedrice.whiteboard.domain.auth.entity.RefreshToken;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.user.repository.PasswordHistoryRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.email.EmailService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
import com.weedrice.whiteboard.domain.auth.service.VerificationCodeService;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionTemplate;
import jakarta.servlet.http.HttpServletRequest;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.authentication.AuthenticationManager;
import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.config.annotation.authentication.builders.AuthenticationManagerBuilder;
import org.springframework.security.core.Authentication;
import org.springframework.security.crypto.password.PasswordEncoder;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

        @Mock
        private UserRepository userRepository;
        @Mock
        private UserPointRepository userPointRepository;
        @Mock
        private UserSettingsRepository userSettingsRepository;
        @Mock
        private PointService pointService;
        @Mock
        private PasswordEncoder passwordEncoder;
        @Mock
        private JwtTokenProvider jwtTokenProvider;
        @Mock
        private AuthenticationManagerBuilder authenticationManagerBuilder;
        @Mock
        private RefreshTokenRepository refreshTokenRepository;
        @Mock
        private LoginHistoryRepository loginHistoryRepository;
        @Mock
        private SocialAccountRepository socialAccountRepository;
        @Mock
        private PasswordHistoryRepository passwordHistoryRepository;
        @Mock
        private VerificationCodeService verificationCodeService;
        @Mock
        private PasswordResetTokenRepository passwordResetTokenRepository;
        @Mock
        private EmailService emailService;
        @Mock
        private GlobalConfigService globalConfigService;
        @Mock
        private TransactionTemplate transactionTemplate;

        @InjectMocks
        private AuthService authService;

        private User user;

        @BeforeEach
        void setUp() {
                user = User.builder()
                                .loginId("testuser")
                                .password("encodedPassword")
                                .email("test@example.com")
                                .displayName("Test User")
                                .build();
                ReflectionTestUtils.setField(user, "userId", 1L);
                ReflectionTestUtils.setField(authService, "passwordResetFrontendUrl", "http://localhost:5173/reset-password?token=");
        }

        @Test
        @DisplayName("회원가입 성공")
        void signup_success() {
                // given
                SignupRequest request = SignupRequest.builder()
                                .loginId("testuser")
                                .password("password123")
                                .email("test@example.com")
                                .displayName("Test User")
                                .build();

                when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
                when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false);
                when(verificationCodeService.isVerified(request.getEmail())).thenReturn(true);
                when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
                when(globalConfigService.getConfig(anyString())).thenReturn("500");
                when(userRepository.save(any(User.class))).thenReturn(user);
                when(userSettingsRepository.save(any(com.weedrice.whiteboard.domain.user.entity.UserSettings.class)))
                        .thenAnswer(invocation -> invocation.getArgument(0));

                // when
                SignupResponse response = authService.signup(request);

                // then
                assertThat(response.getLoginId()).isEqualTo(request.getLoginId());
                assertThat(response.getEmail()).isEqualTo(request.getEmail());
                assertThat(response.getDisplayName()).isEqualTo(request.getDisplayName());
                verify(pointService).addPoint(1L, 500, "회원가입 축하 포인트", 1L, "USER");
        }

        @Test
        @DisplayName("회원가입 실패 - 중복된 로그인 ID")
        void signup_fail_duplicateLoginId() {
                // given
                SignupRequest request = SignupRequest.builder()
                                .loginId("testuser")
                                .password("password123")
                                .email("test@example.com")
                                .displayName("Test User")
                                .build();
                when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.empty());
                when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(true);

                // when & then
                BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));
                assertThat(exception.getErrorCode().getCode()).isEqualTo("U002");
        }

        @Test
        @DisplayName("회원가입 실패 - 중복된 이메일")
        void signup_fail_duplicateEmail() {
                // given
                SignupRequest request = SignupRequest.builder()
                                .loginId("testuser")
                                .password("password123")
                                .email("test@example.com")
                                .displayName("Test User")
                                .build();
                User activeUser = User.builder()
                                .loginId("other")
                                .email("test@example.com")
                                .displayName("Other")
                                .password("pwd")
                                .build();
                ReflectionTestUtils.setField(activeUser, "status", "ACTIVE");
                when(userRepository.findByEmail(request.getEmail())).thenReturn(Optional.of(activeUser));

                // when & then
                BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));
                assertThat(exception.getErrorCode().getCode()).isEqualTo("U003");
        }

        @Test
        @DisplayName("로그인 성공")
        void login_success() {
                // given
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
                when(jwtTokenProvider.createAccessToken(authentication)).thenReturn("accessToken");
                when(jwtTokenProvider.createRefreshToken(authentication)).thenReturn("refreshToken");

                // when
                LoginResponse response = authService.login(request, httpServletRequest);

                // then
                assertThat(response.getAccessToken()).isEqualTo("accessToken");
                assertThat(response.getRefreshToken()).isEqualTo("refreshToken");
                assertThat(response.getUser().getLoginId()).isEqualTo("testuser");
        }

        @Test
        @DisplayName("로그인 ID 찾기 성공")
        void findLoginId_success() {
                // given
                String email = "test@example.com";
                when(verificationCodeService.isVerified(email)).thenReturn(true);
                when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

                // when
                com.weedrice.whiteboard.domain.auth.dto.FindIdResponse response = authService.findLoginId(email);

                // then
                assertThat(response.getLoginId()).isEqualTo("testuser");
                verify(verificationCodeService).isVerified(email);
                verify(userRepository).findByEmail(email);
        }

        @Test
        @DisplayName("비밀번호 초기화 링크 발송 (이메일 입력) - 성공")
        void sendPasswordResetLinkByEmail_success() {
                // given
                String email = "test@example.com";
                when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

                // when
                authService.sendPasswordResetLinkByEmail(email);

                // then
                verify(userRepository).findByEmail(email);
                verify(emailService).sendEmail(eq("test@example.com"), anyString(), anyString());
        }

        @Test
        @DisplayName("비밀번호 초기화 링크 발송 (이메일 입력) - 사용자 없음")
        void sendPasswordResetLinkByEmail_userNotFound() {
                // given
                String email = "unknown@example.com";
                when(userRepository.findByEmail(email)).thenReturn(Optional.empty());

                // when & then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> authService.sendPasswordResetLinkByEmail(email));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_NOT_FOUND_BY_EMAIL);
        }

        @Test
        @DisplayName("비밀번호 초기화 링크 발송 (이메일 입력) - DELETED 사용자")
        void sendPasswordResetLinkByEmail_deletedUser() {
                // given
                String email = "test@example.com";
                ReflectionTestUtils.setField(user, "status", "DELETED");
                when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));

                // when & then
                BusinessException exception = assertThrows(BusinessException.class,
                        () -> authService.sendPasswordResetLinkByEmail(email));
                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.USER_DELETED);
        }

        @Test
        @DisplayName("resetPasswordByCode validates verification code before reset")
        void resetPasswordByCode_success() {
                String email = "test@example.com";
                String code = "123456";
                String newPassword = "newPassword123!";

                when(verificationCodeService.verifyCode(email, code))
                                .thenReturn(new VerifyCodeResponse(true, null, false));
                when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
                when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
                when(refreshTokenRepository.findByUserAndIsRevoked(user, false)).thenReturn(Collections.emptyList());
                when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
                when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

                authService.resetPasswordByCode(email, code, newPassword);

                verify(verificationCodeService).verifyCode(email, code);
                verify(verificationCodeService, never()).isVerified(anyString());
                verify(userRepository).save(user);
                verify(passwordHistoryRepository).save(any());
                verify(verificationCodeService).clearVerificationStatus(email);
        }

        @Test
        @DisplayName("resetPasswordByCode revokes active refresh tokens")
        void resetPasswordByCode_revokesRefreshTokens() {
                String email = "test@example.com";
                String code = "123456";
                String newPassword = "newPassword123!";
                RefreshToken refreshToken = RefreshToken.builder()
                                .user(user)
                                .tokenHash("hash")
                                .ipAddress("127.0.0.1")
                                .deviceInfo("browser")
                                .expiresAt(LocalDateTime.now().plusDays(1))
                                .build();

                when(verificationCodeService.verifyCode(email, code))
                                .thenReturn(new VerifyCodeResponse(true, null, false));
                when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
                when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
                when(refreshTokenRepository.findByUserAndIsRevoked(user, false)).thenReturn(List.of(refreshToken));
                when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
                when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

                authService.resetPasswordByCode(email, code, newPassword);

                assertThat(refreshToken.getIsRevoked()).isTrue();
                verify(refreshTokenRepository).saveAll(any());
        }

        @Test
        @DisplayName("resetPasswordWithToken stores history and clears sessions")
        void resetPasswordWithToken_success() {
                String rawToken = "raw-token";
                String newPassword = "newPassword123!";
                PasswordResetToken passwordResetToken = PasswordResetToken.builder()
                                .token("hashed")
                                .user(user)
                                .expiryDate(LocalDateTime.now().plusMinutes(10))
                                .build();
                ReflectionTestUtils.setField(passwordResetToken, "tokenId", 1L);
                ReflectionTestUtils.setField(passwordResetToken, "createdAt", LocalDateTime.now());
                passwordResetToken.markSent();
                RefreshToken refreshToken = RefreshToken.builder()
                                .user(user)
                                .tokenHash("hash")
                                .ipAddress("127.0.0.1")
                                .deviceInfo("browser")
                                .expiresAt(LocalDateTime.now().plusDays(1))
                                .build();

                when(passwordResetTokenRepository.findByToken(anyString())).thenReturn(Optional.of(passwordResetToken));
                when(passwordResetTokenRepository.findByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(passwordResetToken));
                when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(Collections.emptyList());
                when(refreshTokenRepository.findByUserAndIsRevoked(user, false)).thenReturn(List.of(refreshToken));
                when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
                when(passwordEncoder.encode(newPassword)).thenReturn("encodedNewPassword");

                authService.resetPasswordWithToken(rawToken, newPassword);

                assertThat(passwordResetToken.getIsUsed()).isTrue();
                assertThat(refreshToken.getIsRevoked()).isTrue();
                verify(passwordHistoryRepository).save(any());
                verify(verificationCodeService).clearVerificationStatus(emailOf(user));
        }

        @Test
        @DisplayName("resetPasswordByCode rejects recently used passwords")
        void resetPasswordByCode_recentlyUsed() {
                String email = "test@example.com";
                String code = "123456";
                String newPassword = "newPassword123!";
                var recentHistory = com.weedrice.whiteboard.domain.user.entity.PasswordHistory.builder()
                                .user(user)
                                .passwordHash("recentHash")
                                .build();

                when(verificationCodeService.verifyCode(email, code))
                                .thenReturn(new VerifyCodeResponse(true, null, false));
                when(userRepository.findByEmail(email)).thenReturn(Optional.of(user));
                when(passwordHistoryRepository.findTop3ByUserOrderByCreatedAtDesc(user)).thenReturn(List.of(recentHistory));
                when(passwordEncoder.matches(newPassword, "encodedPassword")).thenReturn(false);
                when(passwordEncoder.matches(newPassword, "recentHash")).thenReturn(true);

                BusinessException exception = assertThrows(BusinessException.class,
                                () -> authService.resetPasswordByCode(email, code, newPassword));

                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.PASSWORD_RECENTLY_USED);
                verify(userRepository, never()).save(any());
        }

        @Test
        @DisplayName("resetPasswordByCode fails when verification code is invalid")
        void resetPasswordByCode_invalidCode() {
                String email = "test@example.com";
                String code = "123456";

                when(verificationCodeService.verifyCode(email, code))
                                .thenThrow(new BusinessException(ErrorCode.VALIDATION_ERROR));

                BusinessException exception = assertThrows(BusinessException.class,
                                () -> authService.resetPasswordByCode(email, code, "newPassword123!"));

                assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
                verify(userRepository, never()).findByEmail(anyString());
        }

        private String emailOf(User targetUser) {
                return targetUser.getEmail();
        }
}
