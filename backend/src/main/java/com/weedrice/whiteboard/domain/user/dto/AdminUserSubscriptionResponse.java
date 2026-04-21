package com.weedrice.whiteboard.domain.user.dto;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.entity.BoardSubscription;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AdminUserSubscriptionResponse {

    private Long boardId;
    private String boardName;
    private String boardUrl;
    private Integer sortOrder;
    private String role;
    private boolean boardActive;
    private boolean boardPublic;
    private boolean subscriptionAccessible;
    private String inaccessibleReason;

    public static AdminUserSubscriptionResponse from(BoardSubscription subscription, boolean subscriptionAccessible) {
        Board board = subscription.getBoard();
        return AdminUserSubscriptionResponse.builder()
                .boardId(board.getBoardId())
                .boardName(board.getBoardName())
                .boardUrl(board.getBoardUrl())
                .sortOrder(subscription.getSortOrder())
                .role(subscription.getRole())
                .boardActive(Boolean.TRUE.equals(board.getIsActive()))
                .boardPublic(Boolean.TRUE.equals(board.getIsPublic()))
                .subscriptionAccessible(subscriptionAccessible)
                .inaccessibleReason(resolveInaccessibleReason(board, subscriptionAccessible))
                .build();
    }

    private static String resolveInaccessibleReason(Board board, boolean subscriptionAccessible) {
        if (subscriptionAccessible) {
            return null;
        }
        if (!Boolean.TRUE.equals(board.getIsActive())) {
            return "INACTIVE";
        }
        if (!Boolean.TRUE.equals(board.getIsPublic())) {
            return "PRIVATE";
        }
        return "RESTRICTED";
    }
}
