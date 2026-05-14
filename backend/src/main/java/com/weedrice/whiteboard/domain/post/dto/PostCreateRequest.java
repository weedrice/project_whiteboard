package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
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

    @Size(max = 100000, message = "본문은 100,000자를 초과할 수 없습니다")
    private String contents;

    @Size(max = TagConstraints.MAX_POST_TAG_COUNT, message = "태그 개수 제한을 초과했습니다")
    private List<@NotBlank(message = "태그명은 공백일 수 없습니다")
            @Size(max = TagConstraints.MAX_TAG_NAME_LENGTH, message = "태그명 길이 제한을 초과했습니다") String> tags;
    @JsonProperty("isNotice")
    private boolean isNotice = false;

    @JsonProperty("isNsfw")
    private boolean isNsfw = false;

    @JsonProperty("isSpoiler")
    private boolean isSpoiler = false;

    @JsonProperty("isSecret")
    private boolean isSecret = false;

    private Long draftId;

    private List<Long> fileIds;

    public PostCreateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNotice, boolean isNsfw, boolean isSpoiler, boolean isSecret, List<Long> fileIds) {
        this(categoryId, title, contents, tags, isNotice, isNsfw, isSpoiler, isSecret, null, fileIds);
    }
}
