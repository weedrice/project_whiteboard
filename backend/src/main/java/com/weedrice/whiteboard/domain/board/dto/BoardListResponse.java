package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.entity.Board;
import lombok.Getter;

@Getter
public class BoardListResponse {
    private final Long boardId;
    private final String boardName;
    private final String boardUrl;
    private final String description;
    private final String iconUrl;
    private final Integer sortOrder;
    private final long subscriberCount;
    private final long postCount;
    private final String adminDisplayName;

    @JsonProperty("isSubscribed")
    @Getter(onMethod_ = @JsonProperty("isSubscribed"))
    private final boolean isSubscribed;

    @JsonProperty("isActive")
    @Getter(onMethod_ = @JsonProperty("isActive"))
    private final boolean isActive;

    @JsonProperty("isPublic")
    @Getter(onMethod_ = @JsonProperty("isPublic"))
    private final boolean isPublic;

    @JsonProperty("subscriptionAccessible")
    private final boolean subscriptionAccessible;

    public BoardListResponse(Board board,
            long subscriberCount,
            long postCount,
            String adminDisplayName,
            boolean isSubscribed) {
        this(board, subscriberCount, postCount, adminDisplayName, isSubscribed, true);
    }

    public BoardListResponse(Board board,
            long subscriberCount,
            long postCount,
            String adminDisplayName,
            boolean isSubscribed,
            boolean subscriptionAccessible) {
        this(
                board.getBoardId(),
                board.getBoardName(),
                board.getBoardUrl(),
                board.getDescription(),
                board.getIconUrl(),
                board.getSortOrder(),
                subscriberCount,
                postCount,
                adminDisplayName,
                isSubscribed,
                board.getIsActive(),
                board.getIsPublic(),
                subscriptionAccessible);
    }

    protected BoardListResponse(
            Long boardId,
            String boardName,
            String boardUrl,
            String description,
            String iconUrl,
            Integer sortOrder,
            long subscriberCount,
            long postCount,
            String adminDisplayName,
            boolean isSubscribed,
            boolean isActive,
            boolean isPublic,
            boolean subscriptionAccessible) {
        this.boardId = boardId;
        this.boardName = boardName;
        this.boardUrl = boardUrl;
        this.description = description;
        this.iconUrl = iconUrl;
        this.sortOrder = sortOrder;
        this.subscriberCount = subscriberCount;
        this.postCount = postCount;
        this.adminDisplayName = adminDisplayName;
        this.isSubscribed = isSubscribed;
        this.isActive = isActive;
        this.isPublic = isPublic;
        this.subscriptionAccessible = subscriptionAccessible;
    }
}
