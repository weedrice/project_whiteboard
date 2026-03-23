package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.domain.agent.service.AgentService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockHttpServletRequest;
import org.springframework.mock.web.MockHttpServletResponse;

import jakarta.servlet.FilterChain;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

@ExtendWith(MockitoExtension.class)
class AgentAuthenticationFilterTest {

    @Mock
    private AgentService agentService;

    @Test
    @DisplayName("register request from localhost passes without bearer token")
    void registerFromLocalhost_passes() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentService, "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agents/register");
        request.addHeader("X-NoviIs-Agent", "true");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();
        FilterChain chain = mock(FilterChain.class);

        filter.doFilter(request, response, chain);

        assertThat(response.getStatus()).isEqualTo(200);
        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("register request from non-local address is forbidden")
    void registerFromNonLocal_forbidden() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentService, "");
        MockHttpServletRequest request = new MockHttpServletRequest("POST", "/api/v1/agents/register");
        request.addHeader("X-NoviIs-Agent", "true");
        request.setRemoteAddr("10.0.0.5");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(403);
        verifyNoInteractions(agentService);
    }

    @Test
    @DisplayName("non-register agent request without bearer token is unauthorized")
    void statusWithoutBearer_unauthorized() throws Exception {
        AgentAuthenticationFilter filter = new AgentAuthenticationFilter(agentService, "");
        MockHttpServletRequest request = new MockHttpServletRequest("GET", "/api/v1/agents/status");
        request.addHeader("X-NoviIs-Agent", "true");
        request.setRemoteAddr("127.0.0.1");
        MockHttpServletResponse response = new MockHttpServletResponse();

        filter.doFilter(request, response, mock(FilterChain.class));

        assertThat(response.getStatus()).isEqualTo(401);
        verifyNoInteractions(agentService);
    }
}
