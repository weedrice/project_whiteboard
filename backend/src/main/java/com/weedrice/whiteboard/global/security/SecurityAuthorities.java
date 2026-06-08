package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.user.entity.Role;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.authority.SimpleGrantedAuthority;

import java.util.ArrayList;
import java.util.List;

public final class SecurityAuthorities {

    private SecurityAuthorities() {
    }

    public static List<GrantedAuthority> user(boolean superAdmin) {
        List<GrantedAuthority> authorities = new ArrayList<>();
        authorities.add(authority(Role.ROLE_USER));
        if (superAdmin) {
            authorities.add(authority(Role.ROLE_SUPER_ADMIN));
        }
        return authorities;
    }

    public static List<GrantedAuthority> single(String authorityName) {
        return List.of(authority(authorityName));
    }

    public static List<GrantedAuthority> guest() {
        return single(Role.ROLE_GUEST);
    }

    private static GrantedAuthority authority(String authority) {
        return new SimpleGrantedAuthority(authority);
    }
}
