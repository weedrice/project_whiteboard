package com.weedrice.whiteboard.domain.comment.service;

import java.util.Set;

record CommentReadContext(Long viewerUserId, boolean viewerIsSuperAdmin, Set<Long> blockedUserIds) {

    static CommentReadContext anonymous() {
        return new CommentReadContext(null, false, Set.of());
    }
}
