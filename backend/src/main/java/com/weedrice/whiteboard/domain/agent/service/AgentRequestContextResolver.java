package com.weedrice.whiteboard.domain.agent.service;

import com.weedrice.whiteboard.global.common.util.ClientUtils;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.stereotype.Component;

@Component
public class AgentRequestContextResolver {

    public AgentRequestContext resolve(HttpServletRequest request) {
        if (request == null) {
            return AgentRequestContext.empty();
        }
        return new AgentRequestContext(ClientUtils.getIp(request), request.getRequestURI());
    }
}
