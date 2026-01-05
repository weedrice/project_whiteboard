package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.LoginRequest;
import com.weedrice.whiteboard.domain.auth.dto.LoginResponse;
import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.SocialAccountRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import com.weedrice.whiteboard.global.security.JwtTokenProvider;
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

import java.util.Collections;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
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

                when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false);
                when(userRepository.existsByEmail(request.getEmail())).thenReturn(false);
                when(passwordEncoder.encode(request.getPassword())).thenReturn("encodedPassword");
                when(userRepository.save(any(User.class))).thenReturn(user);

                // when
                SignupResponse response = authService.signup(request);

                // then
                assertThat(response.getLoginId()).isEqualTo(request.getLoginId());
                assertThat(response.getEmail()).isEqualTo(request.getEmail());
                assertThat(response.getDisplayName()).isEqualTo(request.getDisplayName());
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
                when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false);
                when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

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
}
