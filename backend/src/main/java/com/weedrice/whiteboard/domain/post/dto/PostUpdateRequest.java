package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.fasterxml.jackson.annotation.JsonSetter;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PostUpdateRequest {

    private Long categoryId;

    @NotBlank
    @Size(min = 1, max = 200)
    @NoHtml
    private String title;

    @Size(max = 100000, message = "{validation.post.content.max}")
    private String contents;

    @Size(max = TagConstraints.MAX_POST_TAG_COUNT, message = "{validation.post.tags.max}")
    private List<@NotBlank(message = "{validation.post.tagName.required}")
            @Size(max = TagConstraints.MAX_TAG_NAME_LENGTH, message = "{validation.post.tagName.max}") String> tags;
    @JsonProperty("isNsfw")
    @Getter(onMethod_ = @JsonProperty("isNsfw"))
    private boolean isNsfw;

    @JsonProperty("isSpoiler")
    @Getter(onMethod_ = @JsonProperty("isSpoiler"))
    private boolean isSpoiler;

    @JsonProperty("isSecret")
    @Getter(onMethod_ = @JsonProperty("isSecret"))
    private boolean isSecret;

    @JsonProperty("isNotice")
    @Getter(onMethod_ = @JsonProperty("isNotice"))
    private Boolean isNotice;

    private Long draftId;

    @Size(max = FileAssociationConstraints.MAX_POST_FILE_COUNT, message = "{validation.post.files.max}")
    private List<Long> fileIds;

    @Valid
    private PollRequest poll;
    private Long seriesId;
    private boolean seriesIdPresent;

    public PostUpdateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNsfw, boolean isSpoiler, boolean isSecret, Long draftId, List<Long> fileIds) {
        this(categoryId, title, contents, tags, isNsfw, isSpoiler, isSecret, null, draftId, fileIds, null, null);
    }

    public PostUpdateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNsfw, boolean isSpoiler, boolean isSecret, List<Long> fileIds) {
        this(categoryId, title, contents, tags, isNsfw, isSpoiler, isSecret, null, fileIds);
    }

    public PostUpdateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNsfw, boolean isSpoiler, boolean isSecret, Long draftId, List<Long> fileIds,
            PollRequest poll, Long seriesId) {
        this(categoryId, title, contents, tags, isNsfw, isSpoiler, isSecret, null, draftId, fileIds, poll, seriesId);
    }

    public PostUpdateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNsfw, boolean isSpoiler, boolean isSecret, Boolean isNotice, Long draftId, List<Long> fileIds,
            PollRequest poll, Long seriesId) {
        this.categoryId = categoryId;
        this.title = title;
        this.contents = contents;
        this.tags = tags;
        this.isNsfw = isNsfw;
        this.isSpoiler = isSpoiler;
        this.isSecret = isSecret;
        this.isNotice = isNotice;
        this.draftId = draftId;
        this.fileIds = fileIds;
        this.poll = poll;
        this.seriesId = seriesId;
        this.seriesIdPresent = seriesId != null;
    }

    @JsonSetter("seriesId")
    public void setSeriesId(Long seriesId) {
        this.seriesId = seriesId;
        this.seriesIdPresent = true;
    }

    @JsonIgnore
    public boolean isSeriesIdPresent() {
        return seriesIdPresent;
    }
}
