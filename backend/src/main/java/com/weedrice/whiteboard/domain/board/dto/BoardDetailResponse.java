package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import lombok.Getter;

import java.util.List;

@Getter
public class BoardDetailResponse extends BoardListResponse {

    @JsonProperty("isAdmin")
    private final boolean isAdmin;

    @JsonProperty("allowNsfw")
    private final boolean allowNsfw;

    private final List<CategoryResponse> categories;
    private final List<PostSummary> latestPosts;
    private final Long adminUserId;
    private final boolean agentUseYn;
    private final String guidePrompt;

    public BoardDetailResponse(
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
        super(board, subscriberCount, adminDisplayName, isSubscribed);
        this.adminUserId = adminUserId;
        this.isAdmin = isAdmin;
        this.allowNsfw = board.getAllowNsfw();
        this.categories = categories;
        this.latestPosts = latestPosts;
        this.agentUseYn = agentUseYn;
        this.guidePrompt = guidePrompt;
    }
}
