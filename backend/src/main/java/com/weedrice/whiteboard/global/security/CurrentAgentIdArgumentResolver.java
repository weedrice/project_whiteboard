package com.weedrice.whiteboard.global.security;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.core.MethodParameter;
import org.springframework.security.core.Authentication;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;

@Component
public class CurrentAgentIdArgumentResolver implements HandlerMethodArgumentResolver {

    @Override
    public boolean supportsParameter(MethodParameter parameter) {
        return parameter.hasParameterAnnotation(CurrentAgentId.class)
                && Long.class.isAssignableFrom(parameter.getParameterType());
    }

    @Override
    public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
            NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
        CurrentAgentId currentAgentId = parameter.getParameterAnnotation(CurrentAgentId.class);
        Long agentId = currentAgentId();
        if (currentAgentId != null && currentAgentId.required() && agentId == null) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return agentId;
    }

    private Long currentAgentId() {
        Authentication authentication = SecurityContextHolder.getContext().getAuthentication();
        if (authentication == null || !(authentication.getPrincipal() instanceof AgentPrincipal agentPrincipal)) {
            return null;
        }
        return agentPrincipal.getAgentId();
    }
}
