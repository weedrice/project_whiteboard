package com.weedrice.whiteboard.domain.post.dto;

import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class DraftResponse {
    private Long draftId;
    private Long boardId;
    private String title;
    private String contents;
    private Long originalPostId;
    private LocalDateTime modifiedAt;

    public static DraftResponse from(DraftPost draftPost) {
        return DraftResponse.builder()
                .draftId(draftPost.getDraftId())
                .boardId(draftPost.getBoard().getBoardId())
                .title(draftPost.getTitle())
                .contents(draftPost.getContents())
                .originalPostId(draftPost.getOriginalPost() != null ? draftPost.getOriginalPost().getPostId() : null)
                .modifiedAt(draftPost.getModifiedAt())
                .build();
    }
}
