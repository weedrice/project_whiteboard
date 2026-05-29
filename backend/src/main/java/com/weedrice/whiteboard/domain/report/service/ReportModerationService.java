package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportModerationService {
    private static final int DEFAULT_REPORT_PAGE_SIZE = 20;
    private static final Sort DEFAULT_REPORT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("reportId"));

    private final ReportRepository reportRepository;
    private final UserReadableResolver userReadableResolver;
    private final ReportReadAssembler reportReadAssembler;
    private final ReportModerationCommandService reportModerationCommandService;
    private final ReportModerationReadService reportModerationReadService;

    public Page<ReportResponse> getReports(String status, String targetType, Pageable pageable) {
        String normalizedStatus = ReportStatusNormalizer.normalizeNullable(status);
        String normalizedTargetType = ReportTargetTypeNormalizer.normalizeNullable(targetType);
        Pageable safePageable = normalizeReportPageable(pageable);
        return reportReadAssembler.toAdminResponsePage(
                reportRepository.findAdminReports(normalizedStatus, normalizedTargetType, safePageable));
    }

    private Pageable normalizeReportPageable(Pageable pageable) {
        if (pageable == null || pageable.isUnpaged()) {
            return PageRequestUtils.of(0, DEFAULT_REPORT_PAGE_SIZE, DEFAULT_REPORT_SORT);
        }
        return PageRequestUtils.of(pageable.getPageNumber(), pageable.getPageSize(), DEFAULT_REPORT_SORT);
    }

    public Page<MyReportResponse> getMyReports(Long userId, Pageable pageable) {
        User reporter = userReadableResolver.resolve(userId);
        Pageable safePageable = normalizeReportPageable(pageable);
        return reportReadAssembler.toMyReportResponsePage(
                reportRepository.findByReporterOrderByCreatedAtDescReportIdDesc(reporter, safePageable));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReportResponse processReport(Long adminUserId, Long reportId, String status, String remark) {
        Long processedReportId = reportModerationCommandService.processReport(adminUserId, reportId, status, remark);
        return reportModerationReadService.getAdminReportResponse(processedReportId);
    }

}
