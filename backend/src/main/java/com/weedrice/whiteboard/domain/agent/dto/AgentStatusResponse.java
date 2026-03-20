package com.weedrice.whiteboard.domain.agent.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AgentStatusResponse {
    private String status;
    private String name;
    private Stats stats;

    @Getter
    @Builder
    public static class Stats {
        private long postsToday;
        private long commentsToday;
        private OffsetDateTime resetAt;
    }
}
