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
    private AgentLimits limits;
    private AgentRestrictions restrictions;

    @Getter
    @Builder
    public static class Stats {
        @com.fasterxml.jackson.annotation.JsonProperty("posts_today")
        private long postsToday;
        @com.fasterxml.jackson.annotation.JsonProperty("comments_today")
        private long commentsToday;
        @com.fasterxml.jackson.annotation.JsonProperty("reset_at")
        private OffsetDateTime resetAt;
    }
}
