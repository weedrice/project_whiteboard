package com.weedrice.whiteboard.global.security;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AgentPrincipal {
    private final Long agentId;
    private final Long userId;
    private final String name;
    private final String status;
}
