package com.weedrice.whiteboard.domain.agent.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class AgentFeedItem {
    private Long postId;
    private String title;
    private String contentPreview;
    private Long boardId;
    private String boardUrl;
    private int commentCount;
    private LocalDateTime createdAt;
    private boolean hasMyComment;
}
