package com.weedrice.whiteboard.domain.report.dto;

import com.weedrice.whiteboard.domain.report.entity.Report;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

@Getter
@Builder
public class ReportResponse {
    private Long reportId;
    private Long reporterId;
    private String reporterDisplayName;
    private String targetType;
    private Long targetId;
    /** 대상 표시명 (USER: 닉네임, POST/COMMENT: null이면 targetType #targetId로 표시) */
    private String targetDisplayName;
    /** 대상 로그인 ID (USER일 때만 사용, 닉네임/ID 형태 표기용) */
    private String targetLoginId;
    private String reasonType;
    private String remark;
    private String status;
    private String contents;
    private LocalDateTime createdAt;
    private LocalDateTime updatedAt;
    private Long adminId;

    public static ReportResponse from(Report report) {
        return ReportResponse.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporter().getUserId())
                .reporterDisplayName(report.getReporter().getDisplayName())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetDisplayName(null)
                .targetLoginId(null)
                .reasonType(report.getReasonType())
                .remark(report.getRemark())
                .status(report.getStatus())
                .contents(report.getContents())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .adminId(report.getAdmin() != null ? report.getAdmin().getAdminId() : null)
                .build();
    }
}