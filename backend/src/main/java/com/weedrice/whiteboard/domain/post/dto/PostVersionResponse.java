package com.weedrice.whiteboard.domain.post.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
public class PostVersionResponse {
    private Long versionId;
    private String actionType;
    private String title;
    private LocalDateTime createdAt;

    @Builder
    public PostVersionResponse(Long versionId, String actionType, String title, LocalDateTime createdAt) {
        this.versionId = versionId;
        this.actionType = actionType;
        this.title = title;
        this.createdAt = createdAt;
    }
}
