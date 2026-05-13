package com.weedrice.whiteboard.domain.comment.service;

import java.util.Collection;
import java.util.List;

public record BlockedUserIdsParameter(boolean empty, List<Long> ids) {

    private static final Long EMPTY_SENTINEL = -1L;

    public static BlockedUserIdsParameter from(Collection<Long> blockedUserIds) {
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            return new BlockedUserIdsParameter(true, List.of(EMPTY_SENTINEL));
        }
        return new BlockedUserIdsParameter(false, List.copyOf(blockedUserIds));
    }
}
