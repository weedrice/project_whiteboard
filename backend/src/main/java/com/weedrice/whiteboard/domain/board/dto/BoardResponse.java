package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import lombok.Getter;

import java.util.List;

@Getter
public class BoardResponse {
    private final Long boardId;
    private final String boardName;
    private final String boardUrl;
    private final String description;
    private final String iconUrl;
    private final Integer sortOrder;
    private final long subscriberCount;
    private final String adminDisplayName;

    @JsonProperty("isAdmin")
    private final boolean isAdmin;

    @JsonProperty("allowNsfw")
    private final boolean allowNsfw;

    @JsonProperty("isSubscribed")
    private final boolean isSubscribed;

    private final List<CategoryResponse> categories;
    private final List<PostSummary> latestPosts;
    private final Long adminUserId;

    @JsonProperty("isActive")
    private final boolean isActive;

    @JsonProperty("isPublic")
    private final boolean isPublic;

    @JsonProperty("subscriptionAccessible")
    private final boolean subscriptionAccessible;

    private final boolean agentUseYn;
    private final String guidePrompt;

    public BoardResponse(
            Board board,
            long subscriberCount,
            String adminDisplayName,
            Long adminUserId,
            boolean isAdmin,
            boolean isSubscribed,
            List<CategoryResponse> categories,
            List<PostSummary> latestPosts) {
        this(
                board,
                subscriberCount,
                adminDisplayName,
                adminUserId,
                isAdmin,
                isSubscribed,
                true,
                categories,
                latestPosts,
                board.isAgentEnabled(),
                null);
    }

    public BoardResponse(
            Board board,
            long subscriberCount,
            String adminDisplayName,
            Long adminUserId,
            boolean isAdmin,
            boolean isSubscribed,
            boolean subscriptionAccessible,
            List<CategoryResponse> categories,
            List<PostSummary> latestPosts,
            boolean agentUseYn,
            String guidePrompt) {
        this(
                board.getBoardId(),
                board.getBoardName(),
                board.getBoardUrl(),
                board.getDescription(),
                board.getIconUrl(),
                board.getSortOrder(),
                subscriberCount,
                adminDisplayName,
                adminUserId,
                isAdmin,
                board.getAllowNsfw(),
                isSubscribed,
                categories,
                latestPosts,
                board.getIsActive(),
                board.getIsPublic(),
                subscriptionAccessible,
                agentUseYn,
                guidePrompt);
    }

    private BoardResponse(
            Long boardId,
            String boardName,
            String boardUrl,
            String description,
            String iconUrl,
            Integer sortOrder,
            long subscriberCount,
            String adminDisplayName,
            Long adminUserId,
            boolean isAdmin,
            boolean allowNsfw,
            boolean isSubscribed,
            List<CategoryResponse> categories,
            List<PostSummary> latestPosts,
            boolean isActive,
            boolean isPublic,
            boolean subscriptionAccessible,
            boolean agentUseYn,
            String guidePrompt) {
        this.boardId = boardId;
        this.boardName = boardName;
        this.boardUrl = boardUrl;
        this.description = description;
        this.iconUrl = iconUrl;
        this.sortOrder = sortOrder;
        this.allowNsfw = allowNsfw;
        this.subscriberCount = subscriberCount;
        this.adminDisplayName = adminDisplayName;
        this.adminUserId = adminUserId;
        this.isAdmin = isAdmin;
        this.isSubscribed = isSubscribed;
        this.subscriptionAccessible = subscriptionAccessible;
        this.categories = categories;
        this.latestPosts = latestPosts;
        this.isActive = isActive;
        this.isPublic = isPublic;
        this.agentUseYn = agentUseYn;
        this.guidePrompt = guidePrompt;
    }

    public static BoardResponse unavailableSubscription(Board board) {
        return new BoardResponse(
                board.getBoardId(),
                null,
                board.getBoardUrl(),
                null,
                null,
                board.getSortOrder(),
                0L,
                null,
                null,
                false,
                false,
                true,
                List.of(),
                List.of(),
                board.getIsActive(),
                board.getIsPublic(),
                false,
                false,
                null);
    }
}
