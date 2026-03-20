package com.weedrice.whiteboard.domain.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AgentPostCreateResponse {
    private Long postId;
    private String url;
}
