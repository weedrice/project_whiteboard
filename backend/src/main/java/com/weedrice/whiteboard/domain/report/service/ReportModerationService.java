package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportStatus;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
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
    private final UserRepository userRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final ReportReadAssembler reportReadAssembler;

    public Page<ReportResponse> getReports(String status, String targetType, Pageable pageable) {
        String normalizedStatus = normalizeStatus(status);
        String normalizedTargetType = normalizeTargetType(targetType);
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
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Pageable safePageable = normalizeReportPageable(pageable);
        return reportReadAssembler.toMyReportResponsePage(
                reportRepository.findByReporterOrderByCreatedAtDescReportIdDesc(reporter, safePageable));
    }

    @Transactional
    public ReportResponse processReport(Long adminUserId, Long reportId, String status, String remark) {
        SecurityUtils.validateSuperAdminPermission();
        String normalizedStatus = normalizeTerminalStatus(status);
        String normalizedRemark = ReportRemarkNormalizer.normalize(remark);
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        ModerationActorResolver.ModerationActor moderationActor =
                moderationActorResolver.resolveModerationActor(adminUserId);

        report.processReport(
                moderationActor.admin(),
                moderationActor.user().getUserId(),
                normalizedStatus,
                normalizedRemark);
        reportRepository.save(report);
        return reportReadAssembler.toAdminResponse(report);
    }

    private String normalizeStatus(String status) {
        try {
            ReportStatus reportStatus = ReportStatus.fromNullable(status);
            return reportStatus != null ? reportStatus.name() : null;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String normalizeTargetType(String targetType) {
        try {
            ReportTargetType reportTargetType = ReportTargetType.fromNullable(targetType);
            return reportTargetType != null ? reportTargetType.name() : null;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String normalizeTerminalStatus(String status) {
        try {
            ReportStatus reportStatus = ReportStatus.from(status);
            if (!reportStatus.isTerminal()) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
            return reportStatus.name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

}
