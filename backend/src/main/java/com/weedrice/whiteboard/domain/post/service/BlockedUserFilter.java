package com.weedrice.whiteboard.domain.post.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

record BlockedUserFilter(boolean empty, List<Long> ids) {
    private static final List<Long> NO_BLOCKED_USER_IDS = List.of(-1L);

    static BlockedUserFilter from(Set<Long> blockedUserIds) {
        if (blockedUserIds == null || blockedUserIds.isEmpty()) {
            return new BlockedUserFilter(true, NO_BLOCKED_USER_IDS);
        }
        return new BlockedUserFilter(false, new ArrayList<>(blockedUserIds));
    }
}
