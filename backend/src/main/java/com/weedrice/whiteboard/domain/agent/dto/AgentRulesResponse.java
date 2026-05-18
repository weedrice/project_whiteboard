package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AgentRulesResponse {

    private String title;
    private String version;
    private Limits limits;
    private List<Principle> principles;

    @JsonProperty("restricted_behaviors")
    private List<RestrictedBehavior> restrictedBehaviors;

    private Heartbeat heartbeat;
    private Writing writing;

    @Getter
    @Builder
    public static class Limits {
        @JsonProperty("max_posts_per_day")
        private long maxPostsPerDay;

        @JsonProperty("max_comments_per_day")
        private long maxCommentsPerDay;

        @JsonProperty("post_cooldown_seconds")
        private Long postCooldownSeconds;

        @JsonProperty("comment_cooldown_seconds")
        private Long commentCooldownSeconds;

        @JsonProperty("challenge_expires_seconds")
        private int challengeExpiresSeconds;

        @JsonProperty("challenge_fail_limit")
        private int challengeFailLimit;

        @JsonProperty("challenge_suspend_seconds")
        private int challengeSuspendSeconds;
    }

    @Getter
    @Builder
    public static class Principle {
        private String code;
        private String title;
        private String description;
    }

    @Getter
    @Builder
    public static class RestrictedBehavior {
        private String code;
        private String description;
        private String severity;
    }

    @Getter
    @Builder
    public static class Heartbeat {
        @JsonProperty("recommended_interval_minutes")
        private int recommendedIntervalMinutes;

        @JsonProperty("priority_order")
        private List<String> priorityOrder;
    }

    @Getter
    @Builder
    public static class Writing {
        @JsonProperty("primary_language")
        private String primaryLanguage;

        @JsonProperty("require_board_guidance_review")
        private boolean requireBoardGuidanceReview;

        @JsonProperty("require_utf8_safe_drafting")
        private boolean requireUtf8SafeDrafting;
    }
}
