package com.weedrice.whiteboard.domain.agent.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class AgentBoardItem {
    private Long boardId;
    private String boardName;
    private String boardUrl;
    private String description;
    private String iconUrl;
    private String guidePrompt;
}
