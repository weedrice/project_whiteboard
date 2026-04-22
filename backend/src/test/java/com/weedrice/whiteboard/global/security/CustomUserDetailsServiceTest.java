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
    @DisplayName("loadUserByUsername loads regular user details")
    void loadUserByUsername_success() {
        User user = activeUser("testuser");
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByLoginId("testuser")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("testuser");

        assertThat(userDetails.getUsername()).isEqualTo("testuser");
        assertThat(userDetails.getPassword()).isEqualTo("password");
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.getAuthorities()).extracting("authority")
                .contains(Role.ROLE_USER)
                .doesNotContain(Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("loadUserByUsername grants super admin authority only to usable super admin")
    void loadUserByUsername_usableSuperAdmin() {
        User user = activeUser("admin");
        user.grantSuperAdminRole();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByLoginId("admin")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("admin");

        assertThat(userDetails.getAuthorities()).extracting("authority")
                .contains(Role.ROLE_USER, Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("loadUserByUsername does not grant super admin authority to suspended super admin")
    void loadUserByUsername_suspendedSuperAdmin() {
        User user = activeUser("suspended-admin");
        user.grantSuperAdminRole();
        user.suspend();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByLoginId("suspended-admin")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("suspended-admin");

        assertThat(userDetails.isEnabled()).isFalse();
        assertThat(userDetails.isAccountNonLocked()).isFalse();
        assertThat(userDetails.getAuthorities()).extracting("authority")
                .contains(Role.ROLE_USER)
                .doesNotContain(Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("loadUserByUsername locks banned active user")
    void loadUserByUsername_bannedUserLocked() {
        User user = activeUser("banned");
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByLoginId("banned")).thenReturn(Optional.of(user));
        when(sanctionService.isUserBanned(user)).thenReturn(true);

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("banned");

        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("loadUserByUsername disables deleted user")
    void loadUserByUsername_deletedUserDisabled() {
        User user = activeUser("deleted");
        user.delete();
        ReflectionTestUtils.setField(user, "userId", 1L);
        when(userRepository.findByLoginId("deleted")).thenReturn(Optional.of(user));

        UserDetails userDetails = customUserDetailsService.loadUserByUsername("deleted");

        assertThat(userDetails.isEnabled()).isFalse();
        assertThat(userDetails.isAccountNonLocked()).isFalse();
    }

    @Test
    @DisplayName("loadUserByUsername throws when user is missing")
    void loadUserByUsername_notFound() {
        when(userRepository.findByLoginId("unknown")).thenReturn(Optional.empty());

        assertThatThrownBy(() -> customUserDetailsService.loadUserByUsername("unknown"))
                .isInstanceOf(UsernameNotFoundException.class);
    }

    private User activeUser(String loginId) {
        return User.builder()
                .loginId(loginId)
                .password("password")
                .email(loginId + "@test.com")
                .displayName(loginId)
                .build();
    }
}
