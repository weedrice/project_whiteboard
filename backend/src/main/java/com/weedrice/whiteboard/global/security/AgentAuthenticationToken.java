package com.weedrice.whiteboard.global.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class AgentAuthenticationToken extends AbstractAuthenticationToken {
    private final AgentPrincipal principal;
    private final String credentials;

    public AgentAuthenticationToken(AgentPrincipal principal, String credentials) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.principal = principal;
        this.credentials = credentials;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return credentials;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
