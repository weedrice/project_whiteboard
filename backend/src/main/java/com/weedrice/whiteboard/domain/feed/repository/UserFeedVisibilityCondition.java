package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.user.entity.User;

import java.util.Collection;
import java.util.List;

public record UserFeedVisibilityCondition(
        User targetUser,
        List<Long> blockedUserIds,
        boolean superAdmin,
        List<Long> activeAdminBoardIds) {

    public UserFeedVisibilityCondition {
        blockedUserIds = immutableCopy(blockedUserIds);
        activeAdminBoardIds = immutableCopy(activeAdminBoardIds);
    }

    public static UserFeedVisibilityCondition of(
            User targetUser,
            Collection<Long> blockedUserIds,
            Collection<Long> activeAdminBoardIds) {
        return new UserFeedVisibilityCondition(
                targetUser,
                immutableCopy(blockedUserIds),
                targetUser != null && targetUser.isUsableSuperAdmin(),
                immutableCopy(activeAdminBoardIds));
    }

    public boolean hasBlockedUserIds() {
        return !blockedUserIds.isEmpty();
    }

    public boolean hasActiveAdminBoardIds() {
        return !activeAdminBoardIds.isEmpty();
    }

    private static List<Long> immutableCopy(Collection<Long> source) {
        if (source == null || source.isEmpty()) {
            return List.of();
        }
        return List.copyOf(source);
    }
}
