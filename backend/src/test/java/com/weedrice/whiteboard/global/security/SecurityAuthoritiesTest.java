package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.user.entity.Role;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.core.GrantedAuthority;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class SecurityAuthoritiesTest {

    @Test
    @DisplayName("user authorities include ROLE_USER and optional ROLE_SUPER_ADMIN")
    void user_returnsUserAndOptionalSuperAdminAuthorities() {
        assertThat(authorityNames(SecurityAuthorities.user(false)))
                .containsExactly(Role.ROLE_USER);

        assertThat(authorityNames(SecurityAuthorities.user(true)))
                .containsExactly(Role.ROLE_USER, Role.ROLE_SUPER_ADMIN);
    }

    @Test
    @DisplayName("single and guest authorities preserve one-role policies")
    void singleAndGuest_returnSingleAuthority() {
        assertThat(authorityNames(SecurityAuthorities.single(Role.ROLE_SUPER_ADMIN)))
                .containsExactly(Role.ROLE_SUPER_ADMIN);
        assertThat(authorityNames(SecurityAuthorities.guest()))
                .containsExactly(Role.ROLE_GUEST);
    }

    private List<String> authorityNames(List<GrantedAuthority> authorities) {
        return authorities.stream()
                .map(GrantedAuthority::getAuthority)
                .toList();
    }
}
