package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class PostDraftRequest {
    private Long draftId;
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = "^[a-z0-9_]+$", message = "{validation.board.url.pattern}")
    private String boardUrl;
    private String title;
    @Size(max = 100000, message = "본문은 100,000자를 초과할 수 없습니다")
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
    private LocalDateTime updatedAt;
    private Long originalPostId;

    public PostDraftRequest(Long draftId, String boardUrl, String title, String contents, Long originalPostId) {
        this.draftId = draftId;
        this.boardUrl = boardUrl;
        this.title = title;
        this.contents = contents;
        this.originalPostId = originalPostId;
        this.categoryId = null;
        this.tags = List.of();
        this.isNotice = false;
        this.isNsfw = false;
        this.isSpoiler = false;
        this.isSecret = false;
        this.fileIds = List.of();
        this.updatedAt = null;
    }
}
