package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.security.core.userdetails.UserDetails;
import org.springframework.security.core.userdetails.UsernameNotFoundException;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("사용자 정보 로드 성공")
    void loadUserByUsername_success() {
        // given
        String loginId = "testuser";
        User user = User.builder()
                .loginId(loginId)
                .password("password")
                .build();
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.of(user));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername(loginId);

        // then
        assertThat(userDetails.getUsername()).isEqualTo(loginId);
        assertThat(userDetails).isInstanceOf(CustomUserDetails.class);
    }

    @Test
    @DisplayName("사용자 정보 로드 실패 - 사용자를 찾을 수 없음")
    void loadUserByUsername_fail_userNotFound() {
        // given
        String loginId = "nonexistentuser";
        when(userRepository.findByLoginId(loginId)).thenReturn(Optional.empty());

        // when & then
        assertThrows(UsernameNotFoundException.class, () -> customUserDetailsService.loadUserByUsername(loginId));
    }
}
