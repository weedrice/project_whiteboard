package com.weedrice.whiteboard.domain.agent.dto;

import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.List;

@Getter
@AllArgsConstructor
public class AgentFeedResponse {
    private List<AgentFeedItem> posts;
}
