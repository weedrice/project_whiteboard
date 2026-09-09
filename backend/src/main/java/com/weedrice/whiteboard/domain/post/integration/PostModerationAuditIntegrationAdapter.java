package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.post.port.PostModerationAuditPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostModerationAuditIntegrationAdapter implements PostModerationAuditPort {

    private final UserRepository userRepository;
    private final ModerationAuditLogService moderationAuditLogService;

    @Override
    public void recordUserAction(
            Long managerUserId,
            String action,
            String targetType,
            Long targetId,
            Board board,
            String reason) {
        User manager = userRepository.findById(managerUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        moderationAuditLogService.recordUserAction(manager, action, targetType, targetId, board, reason);
    }
}
