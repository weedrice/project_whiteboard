package com.weedrice.whiteboard.global.security;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.Collections;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class CustomUserDetailsTest {

    @Test
    @DisplayName("CustomUserDetails 생성 및 필드 확인")
    void createCustomUserDetails() {
        Long userId = 1L;
        String username = "testuser";
        String password = "password";
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        CustomUserDetails userDetails = new CustomUserDetails(userId, username, password, authorities);

        assertThat(userDetails.getUserId()).isEqualTo(userId);
        assertThat(userDetails.getUsername()).isEqualTo(username);
        assertThat(userDetails.getPassword()).isEqualTo(password);
        assertThat(userDetails.getAuthorities()).hasSameElementsAs(authorities);
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
    }

    @Test
    @DisplayName("CustomUserDetails 생성 (전체 생성자) 및 필드 확인")
    void createCustomUserDetailsFullConstructor() {
        Long userId = 1L;
        String username = "testuser";
        String password = "password";
        List<GrantedAuthority> authorities = Collections.singletonList(new SimpleGrantedAuthority("ROLE_USER"));

        CustomUserDetails userDetails = new CustomUserDetails(userId, username, password, true, true, true, true, authorities);

        assertThat(userDetails.getUserId()).isEqualTo(userId);
        assertThat(userDetails.getUsername()).isEqualTo(username);
        assertThat(userDetails.getPassword()).isEqualTo(password);
        assertThat(userDetails.getAuthorities()).hasSameElementsAs(authorities);
        assertThat(userDetails.isEnabled()).isTrue();
        assertThat(userDetails.isAccountNonExpired()).isTrue();
        assertThat(userDetails.isAccountNonLocked()).isTrue();
        assertThat(userDetails.isCredentialsNonExpired()).isTrue();
    }
}
