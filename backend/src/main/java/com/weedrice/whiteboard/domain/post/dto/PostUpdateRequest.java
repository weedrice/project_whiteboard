package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

@Getter
@NoArgsConstructor
public class PostUpdateRequest {

    private Long categoryId;

    @NotBlank
    @Size(min = 2, max = 200)
    private String title;

    @NotBlank
    private String contents;

    private List<String> tags;
    private boolean isNsfw;
    private boolean isSpoiler;
}
