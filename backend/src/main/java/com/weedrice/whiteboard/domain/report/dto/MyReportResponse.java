package com.weedrice.whiteboard.domain.report.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class MyReportResponse {
    private Long reportId;
    private String targetType;
    private String targetDisplayName;
    private String reasonType;
    private String status;
    private String contents;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
}
