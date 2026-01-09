package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;
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
    @Size(min = 2, max = 200)
    @NoHtml
    private String title;

    @NotBlank
    @Size(max = 50000, message = "본문은 50,000자를 초과할 수 없습니다")
    private String contents;

    private List<String> tags;
    @JsonProperty("isNsfw")
    private boolean isNsfw;

    @JsonProperty("isSpoiler")
    private boolean isSpoiler;

    private List<Long> fileIds;
}
