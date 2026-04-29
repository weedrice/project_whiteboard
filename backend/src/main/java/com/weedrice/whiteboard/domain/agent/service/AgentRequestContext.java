package com.weedrice.whiteboard.domain.agent.service;

public record AgentRequestContext(String ip, String path) {

    public static AgentRequestContext empty() {
        return new AgentRequestContext(null, null);
    }
}
