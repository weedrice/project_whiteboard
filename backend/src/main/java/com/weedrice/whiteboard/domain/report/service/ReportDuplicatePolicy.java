package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;

final class ReportDuplicatePolicy {

    private static final String PENDING_REPORT_DUPLICATE_INDEX = "uq_reports_pending_user_target";
    private static final String LEGACY_REPORT_DUPLICATE_CONSTRAINT = "uk_reports_user_target";

    private ReportDuplicatePolicy() {
    }

    static void validateNoPendingDuplicate(
            ReportRepository reportRepository,
            User reporter,
            String targetType,
            Long targetId) {
        if (reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, targetType, targetId, Report.STATUS_PENDING)) {
            throw alreadyReported();
        }
    }

    static boolean isDuplicateConflict(DataIntegrityViolationException exception) {
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

    static BusinessException alreadyReported() {
        return new BusinessException(ErrorCode.ALREADY_REPORTED);
    }
}
