package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportReasonType;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
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

    private static final String REPORT_DUPLICATE_CONSTRAINT = "uk_reports_user_target";
    private static final int MAX_REMARK_LENGTH = 255;

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
        String normalizedTargetType = normalizeTargetType(targetType);
        String normalizedReasonType = normalizeReasonType(reasonType);
        String normalizedRemark = normalizeRemark(remark);

        reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, normalizedTargetType, targetId)
                .ifPresent(report -> {
                    throw new BusinessException(ErrorCode.ALREADY_REPORTED);
                });

        reportTargetValidator.validate(normalizedTargetType, targetId, reporter);

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(normalizedTargetType)
                .targetId(targetId)
                .reasonType(normalizedReasonType)
                .remark(normalizedRemark)
                .contents(contents)
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

    private String normalizeTargetType(String targetType) {
        try {
            return ReportTargetType.from(targetType).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
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

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalizedRemark = remark.strip();
        if (normalizedRemark.length() > MAX_REMARK_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalizedRemark;
    }

    private boolean isDuplicateReportConflict(DataIntegrityViolationException exception) {
        Throwable current = exception;
        while (current != null) {
            String message = current.getMessage();
            if (message != null && message.contains(REPORT_DUPLICATE_CONSTRAINT)) {
                return true;
            }
            current = current.getCause();
        }
        return false;
    }
}
