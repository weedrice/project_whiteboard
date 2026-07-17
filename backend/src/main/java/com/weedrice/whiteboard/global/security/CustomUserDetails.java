package com.weedrice.whiteboard.global.security;

import lombok.Getter;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.User;

import java.util.Collection;

@Getter
public class CustomUserDetails extends User {

    private final Long userId;
    private final long securityVersion;

    public CustomUserDetails(Long userId, String username, String password, Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, password, 0L, authorities);
    }

    public CustomUserDetails(Long userId, String username, String password, long securityVersion,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, authorities);
        this.userId = userId;
        this.securityVersion = securityVersion;
    }

    public CustomUserDetails(Long userId, String username, String password, boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired, boolean accountNonLocked, Collection<? extends GrantedAuthority> authorities) {
        this(userId, username, password, 0L, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked,
                authorities);
    }

    public CustomUserDetails(Long userId, String username, String password, long securityVersion,
            boolean enabled, boolean accountNonExpired, boolean credentialsNonExpired, boolean accountNonLocked,
            Collection<? extends GrantedAuthority> authorities) {
        super(username, password, enabled, accountNonExpired, credentialsNonExpired, accountNonLocked, authorities);
        this.userId = userId;
        this.securityVersion = securityVersion;
    }
}
