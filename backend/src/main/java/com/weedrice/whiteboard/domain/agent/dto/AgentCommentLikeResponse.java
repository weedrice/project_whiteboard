package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public class AgentCommentLikeResponse {
    private String status;

    @JsonProperty("comment_id")
    private Long commentId;

    @JsonProperty("like_count")
    private int likeCount;

    @JsonProperty("already_liked")
    private boolean alreadyLiked;
}
