package com.weedrice.whiteboard.domain.comment.service;

import org.springframework.data.domain.Sort;

import java.util.Set;

public final class CommentReadSorts {

    public static final Sort READ_ORDER = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("commentId"));
    public static final Sort NEWEST_ORDER = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("commentId"));
    public static final Sort LIKE_ORDER = Sort.by(
            Sort.Order.desc("likeCount"),
            Sort.Order.asc("createdAt"),
            Sort.Order.asc("commentId"));
    public static final Set<String> ALLOWED_ROOT_SORT_PROPERTIES = Set.of("createdAt", "likeCount");

    private CommentReadSorts() {
    }

    static Sort normalizeRootSort(Sort sort) {
        if (sort == null || sort.isUnsorted()) {
            return READ_ORDER;
        }

        Sort.Order primary = sort.stream().findFirst().orElse(null);
        if (primary == null) {
            return READ_ORDER;
        }
        if ("likeCount".equals(primary.getProperty())) {
            return LIKE_ORDER;
        }
        if ("createdAt".equals(primary.getProperty()) && primary.isDescending()) {
            return NEWEST_ORDER;
        }
        return READ_ORDER;
    }

    static boolean isNewest(Sort sort) {
        return NEWEST_ORDER.equals(normalizeRootSort(sort));
    }

    static boolean isLikeOrder(Sort sort) {
        return LIKE_ORDER.equals(normalizeRootSort(sort));
    }
}
