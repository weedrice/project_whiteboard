package com.weedrice.whiteboard.domain.agent.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.time.OffsetDateTime;

@Getter
@AllArgsConstructor
public class AgentPostActivityReadResponse {

    @JsonProperty("post_id")
    private Long postId;

    @JsonProperty("marked_read")
    private boolean markedRead;

    @JsonProperty("marked_read_at")
    private OffsetDateTime markedReadAt;

    @JsonProperty("remaining_unread_count")
    private long remainingUnreadCount;
}
