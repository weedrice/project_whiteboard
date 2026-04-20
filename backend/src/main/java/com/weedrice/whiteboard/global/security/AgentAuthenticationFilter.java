package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentAuthService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import jakarta.servlet.FilterChain;
import jakarta.servlet.ServletException;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.util.StringUtils;
import org.springframework.web.filter.OncePerRequestFilter;

import java.io.IOException;
import java.util.Set;

@RequiredArgsConstructor
public class AgentAuthenticationFilter extends OncePerRequestFilter {

    private static final String AGENT_PATH_PREFIX = "/api/v1/agents/";
    private static final String REGISTER_PATH = "/api/v1/agents/register";
    private static final String AGENT_HEADER = "X-NoviIs-Agent";
    private static final String INTERNAL_SECRET_HEADER = "X-NoviIs-Internal-Secret";
    private static final String AUTHORIZATION_HEADER = "Authorization";
    private static final String BEARER_PREFIX = "Bearer ";
    private static final Set<String> LOOPBACK_ADDRESSES = Set.of("127.0.0.1", "0:0:0:0:0:0:0:1", "::1");

    private final AgentAuthService agentAuthService;
    private final String internalSecret;

    @Override
    protected boolean shouldNotFilter(HttpServletRequest request) {
        String uri = request.getRequestURI();
        return uri == null || !uri.startsWith(AGENT_PATH_PREFIX);
    }

    @Override
    protected void doFilterInternal(HttpServletRequest request, HttpServletResponse response, FilterChain filterChain)
            throws ServletException, IOException {
        if (!"true".equalsIgnoreCase(request.getHeader(AGENT_HEADER))) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Agent header required");
            return;
        }
        if (!isAllowedInternalRequest(request)) {
            response.sendError(HttpServletResponse.SC_FORBIDDEN, "Internal agent request required");
            return;
        }
        if (REGISTER_PATH.equals(request.getRequestURI())) {
            filterChain.doFilter(request, response);
            return;
        }

        String bearerToken = request.getHeader(AUTHORIZATION_HEADER);
        if (!StringUtils.hasText(bearerToken) || !bearerToken.startsWith(BEARER_PREFIX)) {
            response.sendError(HttpServletResponse.SC_UNAUTHORIZED, "Agent token required");
            return;
        }

        String rawToken = bearerToken.substring(BEARER_PREFIX.length());
        try {
            Agent agent = agentAuthService.authenticate(rawToken);
            AgentPrincipal principal = new AgentPrincipal(
                    agent.getAgentId(),
                    agent.getUser() != null ? agent.getUser().getUserId() : null,
                    agent.getName(),
                    agent.getStatus());
            SecurityContextHolder.getContext().setAuthentication(new AgentAuthenticationToken(principal, rawToken));
            filterChain.doFilter(request, response);
        } catch (BusinessException e) {
            response.sendError(e.getErrorCode().getStatus().value(), e.getMessage());
        }
    }

    private boolean isAllowedInternalRequest(HttpServletRequest request) {
        if (StringUtils.hasText(internalSecret)) {
            return internalSecret.equals(request.getHeader(INTERNAL_SECRET_HEADER));
        }
        String remoteAddr = request.getRemoteAddr();
        return LOOPBACK_ADDRESSES.contains(remoteAddr);
    }
}
