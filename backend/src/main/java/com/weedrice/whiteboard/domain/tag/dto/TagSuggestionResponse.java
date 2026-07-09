package com.weedrice.whiteboard.domain.tag.dto;

import lombok.Builder;
import lombok.Getter;

import java.util.List;

@Getter
@Builder
public class TagSuggestionResponse {
    private List<String> suggestions;
}
