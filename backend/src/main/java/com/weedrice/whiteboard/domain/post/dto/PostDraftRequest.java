package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.board.util.BoardUrlNormalizer;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.post.constant.PostContentConstraints;
import com.weedrice.whiteboard.domain.post.constant.PostDraftPolicy;
import com.weedrice.whiteboard.domain.post.validation.ValidPostContentLength;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
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
    @Size(max = PostDraftPolicy.MAX_CLIENT_DRAFT_KEY_LENGTH)
    @Pattern(regexp = PostDraftPolicy.CLIENT_DRAFT_KEY_PATTERN)
    private String clientDraftKey;
    private Long version;
    @NotBlank
    @Size(max = 100)
    @Pattern(regexp = BoardUrlNormalizer.BOARD_URL_PATTERN, message = "{validation.board.url.pattern}")
    private String boardUrl;
    @Size(max = 200, message = "{validation.post.title.max}")
    @NoHtml
    private String title;
    @Size(max = PostContentConstraints.MAX_STORED_LENGTH, message = "{validation.post.content.max}")
    @ValidPostContentLength
    private String contents;
    private Long categoryId;
    @Size(max = TagConstraints.MAX_POST_TAG_COUNT, message = "{validation.post.tags.max}")
    private List<@NotBlank(message = "{validation.post.tagName.required}")
            @Size(max = TagConstraints.MAX_TAG_NAME_LENGTH, message = "{validation.post.tagName.max}") String> tags;
    @JsonProperty("isNotice")
    @Getter(onMethod_ = @JsonProperty("isNotice"))
    private boolean isNotice;
    @JsonProperty("isNsfw")
    @Getter(onMethod_ = @JsonProperty("isNsfw"))
    private boolean isNsfw;
    @JsonProperty("isSpoiler")
    @Getter(onMethod_ = @JsonProperty("isSpoiler"))
    private boolean isSpoiler;
    @JsonProperty("isSecret")
    @Getter(onMethod_ = @JsonProperty("isSecret"))
    private boolean isSecret;
    @Size(max = FileAssociationConstraints.MAX_POST_FILE_COUNT, message = "{validation.post.files.max}")
    private List<Long> fileIds;
    private PollRequest poll;
    private Long seriesId;
    private LocalDateTime updatedAt;
    private Long originalPostId;

    public PostDraftRequest(Long draftId, String boardUrl, String title, String contents, Long originalPostId) {
        this.draftId = draftId;
        this.clientDraftKey = null;
        this.version = null;
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
