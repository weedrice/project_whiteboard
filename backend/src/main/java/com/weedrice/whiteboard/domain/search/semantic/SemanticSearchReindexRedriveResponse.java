package com.weedrice.whiteboard.domain.search.semantic;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class SemanticSearchReindexRedriveResponse {
    private Long jobId;
    private String status;
}

