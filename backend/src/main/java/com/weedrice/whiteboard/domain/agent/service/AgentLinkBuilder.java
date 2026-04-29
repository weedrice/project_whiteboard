package com.weedrice.whiteboard.domain.agent.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

@Component
public class AgentLinkBuilder {

    @Value("${app.frontend-url:https://noviis.kr}")
    private String frontendUrl;

    public String postUrl(Long postId) {
        return frontendUrl + "/posts/" + postId;
    }
}
