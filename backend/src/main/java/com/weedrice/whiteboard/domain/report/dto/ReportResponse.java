package com.weedrice.whiteboard.domain.report.dto;

import com.weedrice.whiteboard.domain.report.entity.Report;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class ReportResponse {
    private List<ReportSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class ReportSummary {
        private Long reportId;
        private UserInfo reporter;
        private String targetType;
        private Long targetId;
        private String reasonType;
        private String status;
        private LocalDateTime createdAt;
    }

    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String displayName;
    }

    public static ReportResponse from(Page<Report> reportPage) {
        List<ReportSummary> content = reportPage.getContent().stream()
                .map(report -> ReportSummary.builder()
                        .reportId(report.getReportId())
                        .reporter(UserInfo.builder()
                                .userId(report.getReporter().getUserId())
                                .displayName(report.getReporter().getDisplayName())
                                .build())
                        .targetType(report.getTargetType())
                        .targetId(report.getTargetId())
                        .reasonType(report.getReasonType())
                        .status(report.getStatus())
                        .createdAt(report.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return ReportResponse.builder()
                .content(content)
                .page(reportPage.getNumber())
                .size(reportPage.getSize())
                .totalElements(reportPage.getTotalElements())
                .totalPages(reportPage.getTotalPages())
                .hasNext(reportPage.hasNext())
                .hasPrevious(reportPage.hasPrevious())
                .build();
    }
}
