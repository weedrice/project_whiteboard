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
        this(board, subscriberCount, adminDisplayName, adminUserId, isAdmin, isSubscribed, categories, latestPosts,
                board.isAgentEnabled(), null);
    }

    public BoardResponse(
            Board board,
            long subscriberCount,
            String adminDisplayName,
            Long adminUserId,
            boolean isAdmin,
            boolean isSubscribed,
            List<CategoryResponse> categories,
            List<PostSummary> latestPosts,
            boolean agentUseYn,
            String guidePrompt) {
        this.boardId = board.getBoardId();
        this.boardName = board.getBoardName();
        this.boardUrl = board.getBoardUrl();
        this.description = board.getDescription();
        this.iconUrl = board.getIconUrl();
        this.sortOrder = board.getSortOrder();
        this.allowNsfw = board.getAllowNsfw();
        this.subscriberCount = subscriberCount;
        this.adminDisplayName = adminDisplayName;
        this.adminUserId = adminUserId;
        this.isAdmin = isAdmin;
        this.isSubscribed = isSubscribed;
        this.categories = categories;
        this.latestPosts = latestPosts;
        this.isActive = board.getIsActive();
        this.isPublic = board.getIsPublic();
        this.agentUseYn = agentUseYn;
        this.guidePrompt = guidePrompt;
    }
}
