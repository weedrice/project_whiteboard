package com.weedrice.whiteboard.global.security;

import org.springframework.security.authentication.UsernamePasswordAuthenticationToken;
import org.springframework.security.core.GrantedAuthority;
import org.springframework.security.core.userdetails.UserDetails;

import java.util.Collection;
import java.util.UUID;

/**
 * Authentication for a user access token that remains bound to its refresh-token session family.
 */
public final class SessionAuthenticationToken extends UsernamePasswordAuthenticationToken {

    private final UUID sessionFamilyId;

    public SessionAuthenticationToken(
            UserDetails principal,
            Collection<? extends GrantedAuthority> authorities,
            UUID sessionFamilyId) {
        super(principal, null, authorities);
        if (sessionFamilyId == null) {
            throw new IllegalArgumentException("sessionFamilyId is required");
        }
        this.sessionFamilyId = sessionFamilyId;
    }

    public UUID getSessionFamilyId() {
        return sessionFamilyId;
    }
}
