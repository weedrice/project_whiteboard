package com.weedrice.whiteboard.domain.file.entity;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.Locale;

public enum FileVariantType {
    THUMBNAIL("thumbnail", 320),
    MEDIUM("medium", 1280);

    private final String pathSegment;
    private final int maxDimension;

    FileVariantType(String pathSegment, int maxDimension) {
        this.pathSegment = pathSegment;
        this.maxDimension = maxDimension;
    }

    public String getPathSegment() {
        return pathSegment;
    }

    public int getMaxDimension() {
        return maxDimension;
    }

    public static FileVariantType fromPathSegment(String value) {
        if (value == null || value.isBlank()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalized = value.trim().toLowerCase(Locale.ROOT);
        for (FileVariantType type : values()) {
            if (type.pathSegment.equals(normalized)) {
                return type;
            }
        }
        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
    }
}
