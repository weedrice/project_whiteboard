package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import jakarta.validation.Valid;
import lombok.AllArgsConstructor;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
@AllArgsConstructor
public class PostCreateRequest {

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
    @Getter(onMethod_ = @JsonProperty("isNotice"))
    private boolean isNotice = false;

    @Getter(onMethod_ = @JsonProperty("isNsfw"))
    private boolean isNsfw = false;

    @Getter(onMethod_ = @JsonProperty("isSpoiler"))
    private boolean isSpoiler = false;

    @Getter(onMethod_ = @JsonProperty("isSecret"))
    private boolean isSecret = false;

    private Long draftId;

    @Size(max = FileAssociationConstraints.MAX_POST_FILE_COUNT, message = "{validation.post.files.max}")
    private List<Long> fileIds;

    @Valid
    private PollRequest poll;
    private Long seriesId;

    public PostCreateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNotice, boolean isNsfw, boolean isSpoiler, boolean isSecret, Long draftId, List<Long> fileIds) {
        this(categoryId, title, contents, tags, isNotice, isNsfw, isSpoiler, isSecret, draftId, fileIds, null, null);
    }

    public PostCreateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNotice, boolean isNsfw, boolean isSpoiler, boolean isSecret, List<Long> fileIds) {
        this(categoryId, title, contents, tags, isNotice, isNsfw, isSpoiler, isSecret, null, fileIds);
    }
}
