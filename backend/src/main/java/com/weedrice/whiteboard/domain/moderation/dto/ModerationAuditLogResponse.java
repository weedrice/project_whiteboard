package com.weedrice.whiteboard.domain.moderation.dto;

import com.weedrice.whiteboard.domain.moderation.entity.ModerationAuditLog;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ModerationAuditLogResponse {

    private Long auditId;
    private String actorType;
    private Long actorUserId;
    private String actorDisplayName;
    private Long adminId;
    private String action;
    private String targetType;
    private Long targetId;
    private Long boardId;
    private String boardName;
    private String boardUrl;
    private String reason;
    private LocalDateTime createdAt;

    public static ModerationAuditLogResponse from(ModerationAuditLog auditLog) {
        return ModerationAuditLogResponse.builder()
                .auditId(auditLog.getAuditId())
                .actorType(auditLog.getActorType())
                .actorUserId(auditLog.getActorUser() != null ? auditLog.getActorUser().getUserId() : null)
                .actorDisplayName(auditLog.getActorUser() != null ? auditLog.getActorUser().getDisplayName() : null)
                .adminId(auditLog.getAdminId())
                .action(auditLog.getAction())
                .targetType(auditLog.getTargetType())
                .targetId(auditLog.getTargetId())
                .boardId(auditLog.getBoard() != null ? auditLog.getBoard().getBoardId() : null)
                .boardName(auditLog.getBoard() != null ? auditLog.getBoard().getBoardName() : null)
                .boardUrl(auditLog.getBoard() != null ? auditLog.getBoard().getBoardUrl() : null)
                .reason(auditLog.getReason())
                .createdAt(auditLog.getCreatedAt())
                .build();
    }
}
