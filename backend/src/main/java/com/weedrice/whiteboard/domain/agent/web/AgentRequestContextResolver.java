package com.weedrice.whiteboard.domain.agent.web;

import com.weedrice.whiteboard.domain.agent.service.AgentRequestContext;
import com.weedrice.whiteboard.global.common.util.ClientIpResolver;
import jakarta.servlet.http.HttpServletRequest;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class AgentRequestContextResolver {

    private final ClientIpResolver clientIpResolver;

    public AgentRequestContext resolve(HttpServletRequest request) {
        if (request == null) {
            return AgentRequestContext.empty();
        }
        return new AgentRequestContext(clientIpResolver.resolve(request), request.getRequestURI());
    }
}
