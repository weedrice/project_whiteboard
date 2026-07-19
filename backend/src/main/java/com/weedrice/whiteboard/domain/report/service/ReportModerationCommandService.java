package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportModerationCommandService {

    private final ReportRepository reportRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final ModerationAuditLogService moderationAuditLogService;
    private final ReportAutoBlindService reportAutoBlindService;

    @Transactional
    public Long processReport(Long adminUserId, Long reportId, String status, String remark) {
        ModerationActorResolver.ModerationActor moderationActor =
                moderationActorResolver.resolveModerationActor(adminUserId);
        String normalizedStatus = ReportStatusNormalizer.normalizeTerminal(status);
        String normalizedRemark = ReportRemarkNormalizer.normalize(remark);
        Report report = reportRepository.findByIdForUpdate(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        report.processReport(
                moderationActor.admin(),
                moderationActor.user().getUserId(),
                normalizedStatus,
                normalizedRemark);
        reportRepository.save(report);
        if (Report.STATUS_REJECTED.equals(normalizedStatus)) {
            reportAutoBlindService.restoreAutoBlindedCommentIfEligible(
                    report.getTargetType(), report.getTargetId());
        }
        moderationAuditLogService.recordUserAction(
                moderationActor.user(),
                Report.STATUS_RESOLVED.equals(normalizedStatus)
                        ? ModerationAuditLogService.ACTION_REPORT_RESOLVE
                        : ModerationAuditLogService.ACTION_REPORT_REJECT,
                ModerationAuditLogService.TARGET_TYPE_REPORT,
                report.getReportId(),
                null,
                normalizedRemark);
        return report.getReportId();
    }
}
