package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.entity.ReportStatus;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

final class ReportStatusNormalizer {

    private ReportStatusNormalizer() {
    }

    static String normalizeNullable(String status) {
        try {
            ReportStatus reportStatus = ReportStatus.fromNullable(status);
            return reportStatus != null ? reportStatus.name() : null;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }

    static String normalizeTerminal(String status) {
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
