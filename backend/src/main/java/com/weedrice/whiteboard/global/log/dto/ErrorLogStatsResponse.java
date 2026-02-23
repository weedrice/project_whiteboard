package com.weedrice.whiteboard.global.log.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class ErrorLogStatsResponse {

    private long totalCount;
    private long unresolvedCount;
    private long resolvedCount;
    private long serverErrorCount;
    private long clientErrorCount;
}
