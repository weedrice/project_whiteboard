package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.user.entity.Role;
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
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class CustomUserDetailsServiceTest {

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("사용자 이름으로 조회 성공")
    void loadUserByUsername_success() {
        // given
        User user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);

        when(userRepository.findByLoginId("testuser")).thenReturn(Optional.of(user));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");

        // then
        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("testuser");
        assertThat(userDetails.getPassword()).isEqualTo("password");
        assertThat(userDetails.getAuthorities()).extracting("authority").contains(Role.ROLE_USER);
        assertThat(userDetails.getAuthorities()).extracting("authority").doesNotContain(Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("슈퍼 관리자 권한 확인")
    void loadUserByUsername_superAdmin() {
        // given
        User user = User.builder()
                .loginId("admin")
                .password("admin")
                .email("admin@test.com")
                .displayName("Admin")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);

        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(user));

        // when
        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin");

        // then
        assertThat(userDetails.getAuthorities()).extracting("authority").contains(Role.ROLE_USER, Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("사용자 찾을 수 없음")
    void loadUserByUsername_notFound() {
        // given
        when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

        // when & then
        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}