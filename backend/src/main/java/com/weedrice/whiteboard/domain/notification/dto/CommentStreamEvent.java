package com.weedrice.whiteboard.domain.notification.dto;

import com.fasterxml.jackson.annotation.JsonInclude;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL)
public class CommentStreamEvent {

    private String action;
    private Long postId;
    private Long commentId;
    private Long actorUserId;
    private LocalDateTime occurredAt;
}
