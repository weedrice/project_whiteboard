package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.agent.service.AgentAuthService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;
import org.springframework.security.core.context.SecurityContextHolder;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AgentAuthenticationFilterTest {

    @Mock
    private AgentAuthService agentAuthService;

    @Test
    @DisplayName("register request bypasses agent filter")
    void registerRequest_bypassesAgentFilter() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentAuthService, "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agents/register");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(agentAuthService);
    }

    @Test
    @DisplayName("register request bypasses internal secret validation")
    void registerRequest_bypassesInternalSecretValidation() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentAuthService, "internal-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agents/register");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verify(chain).doFilter(request, response);
        verifyNoInteractions(agentAuthService);
    }

    @Test
    @DisplayName("non-register agent request without bearer token is unauthorized")
    void statusWithoutBearer_unauthorized() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentAuthService, "internal-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents/status");
        request.addHeader("X-NoviIs-Agent", "true");
        request.addHeader("X-NoviIs-Internal-Secret", "internal-secret");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(agentAuthService);
    }

    @Test
    @DisplayName("authenticated agent request populates security context and continues filter chain")
    void statusWithBearer_authenticated() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentAuthService, "internal-secret");
        Agent agent = Agent.builder()
                .name("Writer Agent")
                .status(Agent.STATUS_ACTIVE)
                .build();
        org.springframework.test.util.ReflectionTestUtils.setField(agent, "agentId", 7L);

        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents/status");
        request.addHeader("X-NoviIs-Agent", "true");
        request.addHeader("X-NoviIs-Internal-Secret", "internal-secret");
        request.addHeader("Authorization", "Bearer noviis_agt_token");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);
        when(agentAuthService.authenticate("noviis_agt_token")).thenReturn(agent);

        try {
            filter.doFilter(request, response, chain);

            assertThat(response.getStatus()).isEqualTo(200);
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isInstanceOf(AgentAuthenticationToken.class);
            verify(agentAuthService).authenticate("noviis_agt_token");
            verify(chain).doFilter(request, response);
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("agent authentication business exception is mapped to error response")
    void statusWithBearer_authenticationFailureMapped() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentAuthService, "internal-secret");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents/status");
        request.addHeader("X-NoviIs-Agent", "true");
        request.addHeader("X-NoviIs-Internal-Secret", "internal-secret");
        request.addHeader("Authorization", "Bearer noviis_agt_token");
        MockHttpServletResponse response = new MockHttpServletResponse();

        when(agentAuthService.authenticate("noviis_agt_token"))
                .thenThrow(new BusinessException(ErrorCode.UNAUTHORIZED));

        try {
            filter.doFilter(request, response, mock(FilterChain.class));

            assertThat(response.getStatus()).isEqualTo(401);
            verify(agentAuthService).authenticate("noviis_agt_token");
            assertThat(SecurityContextHolder.getContext().getAuthentication()).isNull();
        } finally {
            SecurityContextHolder.clearContext();
        }
    }

    @Test
    @DisplayName("missing internal secret rejects loopback agent requests")
    void missingInternalSecret_rejectsLoopbackAgentRequest() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentAuthService, "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents/status");
        request.addHeader("X-NoviIs-Agent", "true");
        request.addHeader("Authorization", "Bearer noviis_agt_token");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(agentAuthService);
        verifyNoInteractions(chain);
    }
}
