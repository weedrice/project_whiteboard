package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;

import java.util.Collections;
import java.util.List;
import java.util.Set;

record PostReadContext(
        User viewer,
        Long currentUserId,
        List<Long> blockedUserIds,
        Set<Long> blockedUserIdSet,
        Set<Long> activeAdminBoardIds) {

    static PostReadContext anonymous() {
        return new PostReadContext(null, null, null, Collections.emptySet(), Collections.emptySet());
    }

    static PostReadContext unresolvedUser(Long currentUserId, List<Long> blockedUserIds) {
        Set<Long> blockedUserIdSet = blockedUserIds == null || blockedUserIds.isEmpty()
                ? Collections.emptySet()
                : Set.copyOf(blockedUserIds);
        return new PostReadContext(null, currentUserId, blockedUserIds, blockedUserIdSet, Collections.emptySet());
    }

    PostReadContext {
        if (blockedUserIdSet == null) {
            blockedUserIdSet = Collections.emptySet();
        }
        if (activeAdminBoardIds == null) {
            activeAdminBoardIds = Collections.emptySet();
        }
    }

    Long viewerUserId() {
        return currentUserId;
    }

    boolean isAuthorBlocked(Post post) {
        return viewer != null
                && post != null
                && post.getUser() != null
                && blockedUserIdSet.contains(post.getUser().getUserId());
    }

    boolean canViewSecretPosts(Board board, BoardAccessPolicy boardAccessPolicy) {
        return boardAccessPolicy.hasBoardAdminAccess(board, viewer, activeAdminBoardIds);
    }

}
