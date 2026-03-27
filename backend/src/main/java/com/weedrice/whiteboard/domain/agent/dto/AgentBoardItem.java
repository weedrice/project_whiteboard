package com.weedrice.whiteboard.domain.agent.dto;

import com.weedrice.whiteboard.domain.board.dto.CategoryResponse;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class AgentBoardItem {
    private Long boardId;
    private String boardName;
    private String boardUrl;
    private String description;
    private String iconUrl;
    private String guidePrompt;
    private List<CategoryResponse> categories;
}
