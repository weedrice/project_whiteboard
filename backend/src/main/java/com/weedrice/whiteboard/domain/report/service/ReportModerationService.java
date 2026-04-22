package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportStatus;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportModerationService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final ReportReadAssembler reportReadAssembler;

    public Page<ReportResponse> getReports(String status, String targetType, Pageable pageable) {
        return reportReadAssembler.toAdminResponsePage(
                reportRepository.findAdminReports(normalizeStatus(status), normalizeTargetType(targetType), pageable));
    }

    public Page<MyReportResponse> getMyReports(Long userId, Pageable pageable) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return reportReadAssembler.toMyReportResponsePage(
                reportRepository.findByReporterOrderByCreatedAtDesc(reporter, pageable));
    }

    @Transactional
    public ReportResponse processReport(Long adminUserId, Long reportId, String status, String remark) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Admin admin = moderationActorResolver.findActiveAdmin(adminUser).orElse(null);
        String normalizedStatus = normalizeTerminalStatus(status);

        report.processReport(admin, adminUserId, normalizedStatus, remark);
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
