package com.weedrice.whiteboard.domain.post.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Getter;

@Getter
public class PostSeriesRequest {
    @NotBlank
    @Size(max = 120)
    private String title;

    @Size(max = 500)
    private String description;
}
