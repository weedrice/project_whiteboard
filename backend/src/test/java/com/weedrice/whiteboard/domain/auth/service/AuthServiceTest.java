package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.dto.SignupRequest;
import com.weedrice.whiteboard.domain.auth.dto.SignupResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.crypto.password.PasswordEncoder;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AuthServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private PasswordEncoder passwordEncoder;

    @InjectMocks
    private AuthService authService;

    @Test
    @DisplayName("회원가입 성공")
    void signup_success() {
        // given
        SignupRequest request = new SignupRequest("testuser", "password123", "test@example.com", "Test User");
        User user = User.builder()
                .loginId(request.getLoginId())
                .password("encodedPassword")
                .email(request.getEmail())
                .displayName(request.getDisplayName())
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
        SignupRequest request = new SignupRequest("testuser", "password123", "test@example.com", "Test User");
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));
        assertThat(exception.getErrorCode().getCode()).isEqualTo("DUPLICATE_LOGIN_ID");
    }

    @Test
    @DisplayName("회원가입 실패 - 중복된 이메일")
    void signup_fail_duplicateEmail() {
        // given
        SignupRequest request = new SignupRequest("testuser", "password123", "test@example.com", "Test User");
        when(userRepository.existsByLoginId(request.getLoginId())).thenReturn(false);
        when(userRepository.existsByEmail(request.getEmail())).thenReturn(true);

        // when & then
        BusinessException exception = assertThrows(BusinessException.class, () -> authService.signup(request));
        assertThat(exception.getErrorCode().getCode()).isEqualTo("DUPLICATE_EMAIL");
    }
}
