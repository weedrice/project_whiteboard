package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

import com.weedrice.whiteboard.domain.board.entity.Board;
import lombok.Getter;

@Getter
public class BoardResponse {
    private final Long boardId;
    private final String boardName;
    private final String description;
    private final String iconUrl;
    private final long subscriberCount;
    private final String adminDisplayName;
    @JsonProperty("isAdmin")
    private final boolean isAdmin;

    @JsonProperty("isSubscribed")
    private final boolean isSubscribed; // 현재 유저의 구독 여부

    public BoardResponse(Board board, long subscriberCount, String adminDisplayName, boolean isAdmin,
            boolean isSubscribed) {
        this.boardId = board.getBoardId();
        this.boardName = board.getBoardName();
        this.description = board.getDescription();
        this.iconUrl = board.getIconUrl();
        this.subscriberCount = subscriberCount;
        this.adminDisplayName = adminDisplayName;
        this.isAdmin = isAdmin;
        this.isSubscribed = isSubscribed;
    }
}
