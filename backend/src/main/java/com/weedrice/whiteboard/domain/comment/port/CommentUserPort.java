package com.weedrice.whiteboard.domain.comment.port;

import java.util.Set;

/** Consumer-owned boundary for user state needed by comment reads. */
public interface CommentUserPort {

    UserReadContext resolveReadContext(Long userId);

    void validateActive(Long userId);

    boolean isEitherDirectionBlocked(Long firstUserId, Long secondUserId);

    record UserReadContext(Long userId, boolean superAdmin, Set<Long> blockedUserIds) {

        public UserReadContext {
            blockedUserIds = blockedUserIds == null ? Set.of() : Set.copyOf(blockedUserIds);
        }
    }
}
