package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.report.entity.Report;
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
        return report.getReportId();
    }
}
