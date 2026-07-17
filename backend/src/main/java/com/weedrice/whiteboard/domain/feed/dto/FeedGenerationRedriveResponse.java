package com.weedrice.whiteboard.domain.feed.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class FeedGenerationRedriveResponse {
    private Long jobId;
    private String status;
}
