package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.NotNull;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDraftRequest {
    private Long draftId; // 수정 시 필요
    @NotNull
    private String boardUrl;
    private String title;
    private String contents;
    private Long originalPostId; // 기존 게시글 수정 시 해당 게시글 ID
}
