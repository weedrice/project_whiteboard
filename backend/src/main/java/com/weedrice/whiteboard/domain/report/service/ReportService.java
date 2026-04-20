package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportCommandService reportCommandService;
    private final ReportModerationService reportModerationService;

    @Transactional
    public Long createReport(Long reporterId, String targetType, Long targetId, String reasonType, String remark,
            String contents) {
        return reportCommandService.createReport(reporterId, targetType, targetId, reasonType, remark, contents);
    }

    public Page<ReportResponse> getReports(String status, String targetType, Pageable pageable) {
        return reportModerationService.getReports(status, targetType, pageable);
    }

    public Page<MyReportResponse> getMyReports(Long userId, Pageable pageable) {
        return reportModerationService.getMyReports(userId, pageable);
    }

    @Transactional
    public ReportResponse processReport(Long adminUserId, Long reportId, String status, String remark) {
        return reportModerationService.processReport(adminUserId, reportId, status, remark);
    }
}
