package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

final class ReportTargetTypeNormalizer {

    private ReportTargetTypeNormalizer() {
    }

    static String normalizeRequired(String targetType) {
        try {
            return ReportTargetType.from(targetType).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }
    }

    static String normalizeNullable(String targetType) {
        try {
            ReportTargetType reportTargetType = ReportTargetType.fromNullable(targetType);
            return reportTargetType != null ? reportTargetType.name() : null;
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_TARGET);
        }
    }
}
