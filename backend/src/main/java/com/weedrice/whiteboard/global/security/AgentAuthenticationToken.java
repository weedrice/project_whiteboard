package com.weedrice.whiteboard.global.security;

import org.springframework.security.authentication.AbstractAuthenticationToken;
import org.springframework.security.core.authority.AuthorityUtils;

public class AgentAuthenticationToken extends AbstractAuthenticationToken {
    private final AgentPrincipal principal;

    public AgentAuthenticationToken(AgentPrincipal principal) {
        super(AuthorityUtils.NO_AUTHORITIES);
        this.principal = principal;
        setAuthenticated(true);
    }

    @Override
    public Object getCredentials() {
        return null;
    }

    @Override
    public Object getPrincipal() {
        return principal;
    }
}
