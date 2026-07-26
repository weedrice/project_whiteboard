package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.entity.Board;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class SubscriptionBoardResponse {

    public enum AccessState {
        ACCESSIBLE,
        INACCESSIBLE
    }

    public enum InaccessibleReason {
        INACTIVE,
        PRIVATE,
        RESTRICTED
    }

    private final Long boardId;
    private final String boardName;
    private final String boardUrl;
    private final String description;
    private final String iconUrl;
    private final Integer sortOrder;
    private final long subscriberCount;
    private final long postCount;
    private final String adminDisplayName;
    private final LocalDateTime latestPostAt;
    private final long newPostCount;

    @Getter(onMethod_ = @JsonProperty("isSubscribed"))
    private final boolean isSubscribed;

    @Getter(onMethod_ = @JsonProperty("isActive"))
    private final boolean isActive;

    @Getter(onMethod_ = @JsonProperty("isPublic"))
    private final boolean isPublic;

    @JsonProperty("subscriptionAccessible")
    private final boolean subscriptionAccessible;

    @JsonProperty("hasNewPosts")
    private final boolean hasNewPosts;

    private final AccessState accessState;
    private final InaccessibleReason inaccessibleReason;

    private SubscriptionBoardResponse(
            Long boardId,
            String boardName,
            String boardUrl,
            String description,
            String iconUrl,
            Integer sortOrder,
            long subscriberCount,
            long postCount,
            String adminDisplayName,
            LocalDateTime latestPostAt,
            long newPostCount,
            boolean isSubscribed,
            boolean isActive,
            boolean isPublic,
            boolean subscriptionAccessible,
            AccessState accessState,
            InaccessibleReason inaccessibleReason) {
        this.boardId = boardId;
        this.boardName = boardName;
        this.boardUrl = boardUrl;
        this.description = description;
        this.iconUrl = iconUrl;
        this.sortOrder = sortOrder;
        this.subscriberCount = subscriberCount;
        this.postCount = postCount;
        this.adminDisplayName = adminDisplayName;
        this.latestPostAt = latestPostAt;
        this.newPostCount = newPostCount;
        this.hasNewPosts = newPostCount > 0;
        this.isSubscribed = isSubscribed;
        this.isActive = isActive;
        this.isPublic = isPublic;
        this.subscriptionAccessible = subscriptionAccessible;
        this.accessState = accessState;
        this.inaccessibleReason = inaccessibleReason;
    }

    public static SubscriptionBoardResponse accessible(BoardListResponse board) {
        return accessible(board, BoardActivitySummary.empty(board.getBoardId()));
    }

    public static SubscriptionBoardResponse accessible(BoardListResponse board, BoardActivitySummary activity) {
        BoardActivitySummary resolvedActivity = activity != null ? activity : BoardActivitySummary.empty(board.getBoardId());
        return new SubscriptionBoardResponse(
                board.getBoardId(),
                board.getBoardName(),
                board.getBoardUrl(),
                board.getDescription(),
                board.getIconUrl(),
                board.getSortOrder(),
                board.getSubscriberCount(),
                board.getPostCount(),
                board.getAdminDisplayName(),
                resolvedActivity.latestPostAt(),
                resolvedActivity.newPostCount(),
                board.isSubscribed(),
                board.isActive(),
                board.isPublic(),
                true,
                AccessState.ACCESSIBLE,
                null);
    }

    public static SubscriptionBoardResponse inaccessible(Board board) {
        return new SubscriptionBoardResponse(
                board.getBoardId(),
                null,
                board.getBoardUrl(),
                null,
                null,
                board.getSortOrder(),
                0L,
                0L,
                null,
                null,
                0L,
                true,
                Boolean.TRUE.equals(board.getIsActive()),
                Boolean.TRUE.equals(board.getIsPublic()),
                false,
                AccessState.INACCESSIBLE,
                resolveInaccessibleReason(board));
    }

    private static InaccessibleReason resolveInaccessibleReason(Board board) {
        if (!Boolean.TRUE.equals(board.getIsActive())) {
            return InaccessibleReason.INACTIVE;
        }
        if (!Boolean.TRUE.equals(board.getIsPublic())) {
            return InaccessibleReason.PRIVATE;
        }
        return InaccessibleReason.RESTRICTED;
    }
}
