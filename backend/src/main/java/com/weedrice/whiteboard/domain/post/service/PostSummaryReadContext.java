package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.domain.user.entity.User;

import java.util.Collection;
import java.util.Collections;
import java.util.List;
import java.util.Objects;
import java.util.Set;

public final class PostSummaryReadContext {

    private final User viewer;
    private final Long currentUserId;
    private final List<Long> blockedUserIds;
    private final Set<Long> blockedUserIdSet;
    private final Set<Long> activeAdminBoardIds;

    private PostSummaryReadContext(
            Long currentUserId,
            User viewer,
            Collection<Long> blockedUserIds,
            Collection<Long> activeAdminBoardIds) {
        this.viewer = viewer;
        this.currentUserId = currentUserId;
        this.blockedUserIds = immutableList(blockedUserIds);
        this.blockedUserIdSet = immutableSet(this.blockedUserIds);
        this.activeAdminBoardIds = immutableSet(activeAdminBoardIds);
    }

    public static PostSummaryReadContext of(
            Long currentUserId,
            User viewer,
            Collection<Long> blockedUserIds,
            Collection<Long> activeAdminBoardIds) {
        return new PostSummaryReadContext(currentUserId, viewer, blockedUserIds, activeAdminBoardIds);
    }

    public User viewer() {
        return viewer;
    }

    public Long currentUserId() {
        return currentUserId;
    }

    List<Long> blockedUserIds() {
        return blockedUserIds;
    }

    Set<Long> blockedUserIdSet() {
        return blockedUserIdSet;
    }

    Set<Long> activeAdminBoardIds() {
        return activeAdminBoardIds;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) {
            return true;
        }
        if (!(o instanceof PostSummaryReadContext that)) {
            return false;
        }
        return Objects.equals(viewer, that.viewer)
                && Objects.equals(currentUserId, that.currentUserId)
                && Objects.equals(blockedUserIds, that.blockedUserIds)
                && Objects.equals(blockedUserIdSet, that.blockedUserIdSet)
                && Objects.equals(activeAdminBoardIds, that.activeAdminBoardIds);
    }

    @Override
    public int hashCode() {
        return Objects.hash(viewer, currentUserId, blockedUserIds, blockedUserIdSet, activeAdminBoardIds);
    }

    private static List<Long> immutableList(Collection<Long> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptyList();
        }
        return List.copyOf(source);
    }

    private static Set<Long> immutableSet(Collection<Long> source) {
        if (source == null || source.isEmpty()) {
            return Collections.emptySet();
        }
        return Set.copyOf(source);
    }
}
