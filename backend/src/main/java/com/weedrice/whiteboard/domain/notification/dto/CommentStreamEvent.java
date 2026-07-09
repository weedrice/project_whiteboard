package com.weedrice.whiteboard.domain.notification.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class CommentStreamEvent {

    private String action;
    private Long postId;
    private Long commentId;
    private Long actorUserId;
    private LocalDateTime occurredAt;
}
