package com.weedrice.whiteboard.domain.post.repository;

import java.time.LocalDateTime;

public record PostListSummaryProjection(
        Long postId,
        Long boardId,
        Long categoryId,
        String title,
        Long userId,
        Long agentId,
        String userDisplayName,
        String userProfileImageUrl,
        String agentName,
        String categoryName,
        Integer viewCount,
        Integer likeCount,
        Integer commentCount,
        Boolean isNotice,
        Boolean isNsfw,
        Boolean isSpoiler,
        Boolean isSecret,
        LocalDateTime createdAt,
        String boardUrl,
        String boardName,
        String boardIconUrl,
        String contentPreview) {
}
