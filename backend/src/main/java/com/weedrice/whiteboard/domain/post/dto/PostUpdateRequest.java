package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
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
public class PostUpdateRequest {

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
    @JsonProperty("isNsfw")
    private boolean isNsfw;

    @JsonProperty("isSpoiler")
    private boolean isSpoiler;

    @JsonProperty("isSecret")
    private boolean isSecret;

    private Long draftId;

    @Size(max = FileAssociationConstraints.MAX_POST_FILE_COUNT, message = "첨부 파일 개수 제한을 초과했습니다")
    private List<Long> fileIds;

    private PollRequest poll;
    private Long seriesId;

    public PostUpdateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNsfw, boolean isSpoiler, boolean isSecret, Long draftId, List<Long> fileIds) {
        this(categoryId, title, contents, tags, isNsfw, isSpoiler, isSecret, draftId, fileIds, null, null);
    }

    public PostUpdateRequest(Long categoryId, String title, String contents, List<String> tags,
            boolean isNsfw, boolean isSpoiler, boolean isSecret, List<Long> fileIds) {
        this(categoryId, title, contents, tags, isNsfw, isSpoiler, isSecret, null, fileIds);
    }
}
