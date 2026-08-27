package com.weedrice.whiteboard.domain.sanction.service;

import com.weedrice.whiteboard.domain.common.service.CommonCodeReader;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.Locale;
import java.util.Set;

@Component
@RequiredArgsConstructor
class SanctionRequestValidator {

    private static final String SANCTION_TYPE_COMMON_CODE_TYPE = "SANCTION_TYPE";
    private static final String TYPE_BAN = "BAN";
    private static final String TYPE_MUTE = "MUTE";
    private static final String TYPE_WARNING = "WARNING";
    private static final int MAX_REMARK_LENGTH = 255;
    private static final Set<String> ALLOWED_TYPES = Set.of("WARNING", "MUTE", "BAN");

    private final Clock clock;
    private final CommonCodeReader commonCodeReader;

    NormalizedCommand validate(String type, String remark, LocalDateTime endDate,
                               Long contentId, String contentType) {
        String normalizedType = normalizeType(type);
        String normalizedContentType = normalizeContentType(contentId, contentType);
        String normalizedRemark = normalizeRemark(remark);
        validateEndDate(normalizedType, endDate);
        return new NormalizedCommand(normalizedType, normalizedRemark, endDate, contentId, normalizedContentType);
    }

    private String normalizeType(String type) {
        if (type == null || type.isBlank()) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        String normalizedType = type.trim().toUpperCase(Locale.ROOT);
        if (!ALLOWED_TYPES.contains(normalizedType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!commonCodeReader.isActiveDetail(SANCTION_TYPE_COMMON_CODE_TYPE, normalizedType)) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedType;
    }

    private String normalizeContentType(Long contentId, String contentType) {
        boolean hasContentId = contentId != null;
        boolean hasContentType = contentType != null && !contentType.isBlank();
        if (hasContentId != hasContentType) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (!hasContentId) {
            return null;
        }
        if (contentId <= 0) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }

        try {
            return ReportTargetType.from(contentType).name();
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    private String normalizeRemark(String remark) {
        if (remark == null) {
            return null;
        }
        String normalizedRemark = remark.strip();
        if (normalizedRemark.length() > MAX_REMARK_LENGTH) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedRemark;
    }

    private void validateEndDate(String type, LocalDateTime endDate) {
        if (TYPE_WARNING.equals(type) && endDate != null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (TYPE_MUTE.equals(type) && endDate == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        if (endDate != null && !endDate.isAfter(LocalDateTime.now(clock))) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    record NormalizedCommand(String type,
                             String remark,
                             LocalDateTime endDate,
                             Long contentId,
                             String contentType) {
        boolean isPermanentBan() {
            return TYPE_BAN.equalsIgnoreCase(type) && endDate == null;
        }
    }
}
