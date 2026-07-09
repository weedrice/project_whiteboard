package com.weedrice.whiteboard.domain.moderation.dto;

import lombok.Builder;

import java.time.LocalDateTime;

@Builder
public record ModerationAuditLogSearchCondition(
        String action,
        String actorType,
        Long boardId,
        String boardUrl,
        Long actorUserId,
        LocalDateTime createdFrom,
        LocalDateTime createdTo) {
}
