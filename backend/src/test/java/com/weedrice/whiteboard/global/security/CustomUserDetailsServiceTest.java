package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
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

    @Mock
    private SanctionService sanctionService;

    @InjectMocks
    private CustomUserDetailsService customUserDetailsService;

    @Test
    @DisplayName("loads regular user details")
    void loadUserByUsername_success() {
        User user = User.builder()
                .loginId("testuser")
                .password("password")
                .email("test@example.com")
                .displayName("Test User")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", false);

        when(userRepository.findByLoginId("testuser")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");

        assertThat(userDetails).isNotNull();
        assertThat(userDetails.getUsername()).isEqualTo("testuser");
        assertThat(userDetails.getPassword()).isEqualTo("password");
        assertThat(userDetails.getAuthorities()).extracting("authority").contains(Role.ROLE_USER);
        assertThat(userDetails.getAuthorities()).extracting("authority").doesNotContain(Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("loads super admin authorities")
    void loadUserByUsername_superAdmin() {
        User user = User.builder()
                .loginId("admin")
                .password("admin")
                .email("admin@test.com")
                .displayName("Admin")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        ReflectionTestUtils.setField(user, "isSuperAdmin", true);

        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin");

        assertThat(userDetails.getAuthorities()).extracting("authority")
                .contains(Role.ROLE_USER, Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("locks account when active ban exists")
    void loadUserByUsername_bannedUserLocked() {
        User user = User.builder()
                .loginId("banned")
                .password("password")
                .email("banned@test.com")
                .displayName("Banned")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);

        when(userRepository.findByLoginId("banned")).thenReturn(Optional.of(user));
        when(sanctionService.isUserBanned(user)).thenReturn(true);

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("banned");

        assertThat(userDetails.isAccountNonLocked()).isFalse();
        assertThat(userDetails.isEnabled()).isTrue();
    }

    @Test
    @DisplayName("throws when user is missing")
    void loadUserByUsername_notFound() {
        when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class);
    }
}
