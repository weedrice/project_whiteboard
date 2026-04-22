package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.post.entity.DraftPost;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
public class DraftResponse {
    private Long draftId;
    private Long boardId;
    private String boardUrl;
    private String boardName;
    private String title;
    private String contents;
    private Long categoryId;
    private List<String> tags;
    @JsonProperty("isNotice")
    private boolean isNotice;
    @JsonProperty("isNsfw")
    private boolean isNsfw;
    @JsonProperty("isSpoiler")
    private boolean isSpoiler;
    @JsonProperty("isSecret")
    private boolean isSecret;
    private List<Long> fileIds;
    private Long originalPostId;
    private LocalDateTime updatedAt;
    private LocalDateTime modifiedAt;

    public static DraftResponse from(DraftPost draftPost) {
        return DraftResponse.builder()
                .draftId(draftPost.getDraftId())
                .boardId(draftPost.getBoard().getBoardId())
                .boardUrl(draftPost.getBoard().getBoardUrl())
                .boardName(draftPost.getBoard().getBoardName())
                .title(draftPost.getTitle())
                .contents(draftPost.getContents())
                .categoryId(draftPost.getCategory() != null ? draftPost.getCategory().getCategoryId() : null)
                .tags(draftPost.getTags())
                .isNotice(draftPost.isNotice())
                .isNsfw(draftPost.isNsfw())
                .isSpoiler(draftPost.isSpoiler())
                .isSecret(draftPost.isSecret())
                .fileIds(draftPost.getFileIds())
                .originalPostId(draftPost.getOriginalPost() != null ? draftPost.getOriginalPost().getPostId() : null)
                .updatedAt(draftPost.getModifiedAt())
                .modifiedAt(draftPost.getModifiedAt())
                .build();
    }
}
