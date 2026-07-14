package com.weedrice.whiteboard.domain.post.scheduled.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
import com.weedrice.whiteboard.domain.file.support.FileAssociationConstraints;
import com.weedrice.whiteboard.domain.post.dto.PollRequest;
import com.weedrice.whiteboard.domain.tag.constant.TagConstraints;
import com.weedrice.whiteboard.global.validation.NoHtml;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;

@Getter
@NoArgsConstructor
public class ScheduledPostRequest {
    private Long categoryId;

    @NotBlank
    @Size(min = 1, max = 200)
    @NoHtml
    private String title;

    @Size(max = 100000)
    private String contents;

    @Size(max = TagConstraints.MAX_POST_TAG_COUNT)
    private List<@NotBlank @Size(max = TagConstraints.MAX_TAG_NAME_LENGTH) String> tags;

    @JsonProperty("isNotice")
    private boolean isNotice;

    @JsonProperty("isNsfw")
    private boolean isNsfw;

    @JsonProperty("isSpoiler")
    private boolean isSpoiler;

    @JsonProperty("isSecret")
    private boolean isSecret;

    @Size(max = FileAssociationConstraints.MAX_POST_FILE_COUNT)
    private List<Long> fileIds;

    @Valid
    private PollRequest poll;

    private Long seriesId;

    private Long draftId;

    @NotNull
    private LocalDateTime scheduledAt;
}
