package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSummary; // PostSummary import
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
    private final boolean isSubscribed; // 현재 유저의 구독 여부
    private final List<CategoryResponse> categories;
    private final List<PostSummary> latestPosts; // 추가

    private final Long adminUserId; // 추가

    @JsonProperty("isActive")
    private final boolean isActive; // 추가

    public BoardResponse(Board board, long subscriberCount, String adminDisplayName, Long adminUserId, boolean isAdmin,
            boolean isSubscribed, List<CategoryResponse> categories, List<PostSummary> latestPosts) { // 생성자 파라미터 추가
        this.boardId = board.getBoardId();
        this.boardName = board.getBoardName();
        this.boardUrl = board.getBoardUrl(); // 할당
        this.description = board.getDescription();
        this.iconUrl = board.getIconUrl();
        this.sortOrder = board.getSortOrder();
        this.allowNsfw = board.getAllowNsfw();
        this.subscriberCount = subscriberCount;
        this.adminDisplayName = adminDisplayName;
        this.adminUserId = adminUserId; // 할당
        this.isAdmin = isAdmin;
        this.isSubscribed = isSubscribed;
        this.categories = categories;
        this.latestPosts = latestPosts; // 할당
        this.isActive = board.getIsActive(); // 할당
    }
}
