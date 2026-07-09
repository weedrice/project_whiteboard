package com.weedrice.whiteboard.domain.tag.dto;

import jakarta.validation.constraints.Size;
import lombok.Getter;

import java.util.List;

@Getter
public class TagSuggestionRequest {
    @Size(max = 200)
    private String title;

    @Size(max = 5000)
    private String contents;

    @Size(max = 100)
    private String boardUrl;

    private List<String> existingTags;
}
