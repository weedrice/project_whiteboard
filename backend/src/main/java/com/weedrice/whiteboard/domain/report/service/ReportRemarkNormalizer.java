package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

final class ReportRemarkNormalizer {

    private ReportRemarkNormalizer() {
    }

    static String normalize(String remark) {
        if (remark == null) {
            return null;
        }
        String normalizedRemark = remark.strip();
        if (normalizedRemark.length() > ReportConstraints.MAX_REMARK_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalizedRemark;
    }
}
