package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
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
    @Pattern(regexp = BoardUrlNormalizer.BOARD_URL_PATTERN, message = "{validation.board.url.pattern}")
    private String boardUrl;
    private String title;
    @Size(max = 100000, message = "본문은 100,000자를 초과할 수 없습니다")
    private String contents;
    private Long categoryId;
    @Size(max = TagConstraints.MAX_POST_TAG_COUNT, message = "태그 개수 제한을 초과했습니다")
    private List<@NotBlank(message = "태그명은 공백일 수 없습니다")
            @Size(max = TagConstraints.MAX_TAG_NAME_LENGTH, message = "태그명 길이 제한을 초과했습니다") String> tags;
    @JsonProperty("isNotice")
    private boolean isNotice;
    @JsonProperty("isNsfw")
    private boolean isNsfw;
    @JsonProperty("isSpoiler")
    private boolean isSpoiler;
    @JsonProperty("isSecret")
    private boolean isSecret;
    @Size(max = FileAssociationConstraints.MAX_POST_FILE_COUNT, message = "첨부 파일 개수 제한을 초과했습니다")
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
