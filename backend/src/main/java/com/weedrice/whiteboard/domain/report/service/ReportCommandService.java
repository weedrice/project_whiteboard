package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportReasonType;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportCommandService {

    private static final String PENDING_REPORT_DUPLICATE_INDEX = "uq_reports_pending_user_target";
    private static final String LEGACY_REPORT_DUPLICATE_CONSTRAINT = "uk_reports_user_target";

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportTargetValidator reportTargetValidator;
    private final SanctionService sanctionService;

    @Transactional
    public Long createReport(Long reporterId, String targetType, Long targetId, String reasonType, String remark,
            String contents) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(reporter);
        String normalizedTargetType = ReportTargetTypeNormalizer.normalizeRequired(targetType);
        String normalizedReasonType = normalizeReasonType(reasonType);
        String normalizedRemark = ReportRemarkNormalizer.normalize(remark);
        String normalizedContents = normalizeContents(contents);

        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, normalizedTargetType, targetId, Report.STATUS_PENDING)) {
            throw new BusinessException(ErrorCode.ALREADY_REPORTED);
        }

        reportTargetValidator.validate(normalizedTargetType, targetId, reporter);

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(normalizedTargetType)
                .targetId(targetId)
                .reasonType(normalizedReasonType)
                .remark(normalizedRemark)
                .contents(normalizedContents)
                .build();
        try {
            return reportRepository.saveAndFlush(report).getReportId();
        } catch (DataIntegrityViolationException ex) {
            if (isDuplicateReportConflict(ex)) {
                throw new BusinessException(ErrorCode.ALREADY_REPORTED);
            }
            throw ex;
        }
    }

    private String normalizeReasonType(String reasonType) {
        if (reasonType == null || reasonType.isBlank()) {
            return ReportReasonType.ETC.name();
        }
        try {
            return ReportReasonType.from(reasonType).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    private String normalizeContents(String contents) {
        if (contents == null) {
            return null;
        }
        if (contents.length() > ReportConstraints.MAX_CONTENTS_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalizedContents = contents.strip();
        if (normalizedContents.isBlank()) {
            return null;
        }
        return normalizedContents;
    }

    private boolean isDuplicateReportConflict(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && (message.contains(PENDING_REPORT_DUPLICATE_INDEX)
                    || message.contains(LEGACY_REPORT_DUPLICATE_CONSTRAINT))) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
