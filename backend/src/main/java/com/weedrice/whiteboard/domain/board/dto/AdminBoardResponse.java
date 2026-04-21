package com.weedrice.whiteboard.domain.board.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.entity.Board;
import lombok.Getter;

@Getter
public class AdminBoardResponse {
    private final Long boardId;
    private final String boardName;
    private final String boardUrl;
    private final String description;
    private final String iconUrl;
    private final Integer sortOrder;
    private final String adminDisplayName;
    private final Long adminUserId;

    @JsonProperty("allowNsfw")
    private final boolean allowNsfw;

    @JsonProperty("isActive")
    private final boolean isActive;

    @JsonProperty("isPublic")
    private final boolean isPublic;

    private final boolean agentUseYn;
    private final String guidePrompt;

    public AdminBoardResponse(Board board, String adminDisplayName, Long adminUserId, String guidePrompt) {
        this.boardId = board.getBoardId();
        this.boardName = board.getBoardName();
        this.boardUrl = board.getBoardUrl();
        this.description = board.getDescription();
        this.iconUrl = board.getIconUrl();
        this.sortOrder = board.getSortOrder();
        this.adminDisplayName = adminDisplayName;
        this.adminUserId = adminUserId;
        this.allowNsfw = board.getAllowNsfw();
        this.isActive = board.getIsActive();
        this.isPublic = board.getIsPublic();
        this.agentUseYn = board.isAgentEnabled();
        this.guidePrompt = guidePrompt;
    }
}
