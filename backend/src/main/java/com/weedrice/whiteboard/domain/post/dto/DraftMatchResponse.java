package com.weedrice.whiteboard.domain.post.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class DraftMatchResponse {
    private Long draftId;
    private boolean multipleMatchesFound;
}
