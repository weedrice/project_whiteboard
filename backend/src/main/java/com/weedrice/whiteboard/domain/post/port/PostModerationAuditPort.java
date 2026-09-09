package com.weedrice.whiteboard.domain.post.port;

import com.weedrice.whiteboard.domain.board.entity.Board;

/** Consumer-owned boundary for post moderation audit records. */
public interface PostModerationAuditPort {

    void recordUserAction(
            Long managerUserId,
            String action,
            String targetType,
            Long targetId,
            Board board,
            String reason);
}
