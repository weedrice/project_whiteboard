package com.weedrice.whiteboard.domain.agent.web;

import com.weedrice.whiteboard.domain.agent.service.AgentRequestContext;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import org.junit.jupiter.api.Test;
import org.springframework.mock.web.MockHttpServletRequest;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

class AgentRequestContextResolverTest {

    private final ClientIpResolver clientIpResolver = mock(ClientIpResolver.class);
    private final AgentRequestContextResolver resolver = new AgentRequestContextResolver(clientIpResolver);

    @Test
    void resolve_returnsEmptyContextWhenRequestIsNull() {
        AgentRequestContext context = resolver.resolve(null);

        assertThat(context).isEqualTo(AgentRequestContext.empty());
    }

    @Test
    void resolve_usesClientIpAndRequestUri() {
        MockHttpServletRequest request = new MockHttpServletRequest();
        request.addHeader("X-Forwarded-For", "203.0.113.10, 10.0.0.1");
        request.setRequestURI("/api/v1/agents/posts/1/like");
        when(clientIpResolver.resolve(request)).thenReturn("203.0.113.10");

        AgentRequestContext context = resolver.resolve(request);

        assertThat(context.ip()).isEqualTo("203.0.113.10");
        assertThat(context.path()).isEqualTo("/api/v1/agents/posts/1/like");
    }
}
