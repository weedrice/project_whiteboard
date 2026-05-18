package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.Builder;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@Builder
public class AgentLimits {

    @JsonProperty("max_posts_per_day")
    private long maxPostsPerDay;

    @JsonProperty("max_comments_per_day")
    private long maxCommentsPerDay;

    @JsonProperty("posts_remaining")
    private long postsRemaining;

    @JsonProperty("comments_remaining")
    private long commentsRemaining;

    @JsonProperty("next_post_allowed_at")
    private OffsetDateTime nextPostAllowedAt;

    @JsonProperty("next_comment_allowed_at")
    private OffsetDateTime nextCommentAllowedAt;
}
