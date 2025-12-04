package com.weedrice.whiteboard.domain.post.dto;

import com.fasterxml.jackson.annotation.JsonProperty;

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
    @Size(min = 2, max = 200)
    private String title;

    @NotBlank
    private String contents;

    private List<String> tags;
    @JsonProperty("isNotice")
    private boolean isNotice = false;

    @JsonProperty("isNsfw")
    private boolean isNsfw = false;

    @JsonProperty("isSpoiler")
    private boolean isSpoiler = false;

    private List<Long> fileIds;
}
