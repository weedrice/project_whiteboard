package com.weedrice.whiteboard.domain.search.semantic;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SemanticSearchBackfillResponse {
    private int enqueuedCount;
}
