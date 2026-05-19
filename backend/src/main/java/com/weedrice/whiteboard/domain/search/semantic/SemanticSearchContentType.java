package com.weedrice.whiteboard.domain.search.semantic;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

import java.util.Locale;

public enum SemanticSearchContentType {
    ALL,
    POST,
    COMMENT;

    public static SemanticSearchContentType from(String value) {
        if (value == null || value.isBlank()) {
            return ALL;
        }
        try {
            return SemanticSearchContentType.valueOf(value.trim().toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException ex) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
    }

    public boolean includesPosts() {
        return this == ALL || this == POST;
    }

    public boolean includesComments() {
        return this == ALL || this == COMMENT;
    }
}
