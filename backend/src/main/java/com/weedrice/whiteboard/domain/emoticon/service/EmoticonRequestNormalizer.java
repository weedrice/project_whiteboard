package com.weedrice.whiteboard.domain.emoticon.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;

final class EmoticonRequestNormalizer {

    static final int MAX_SEARCH_KEYWORD_LENGTH = 100;
    static final int MAX_TAG_LENGTH = 100;
    static final int MAX_OPTION_LENGTH = 20;

    private EmoticonRequestNormalizer() {
    }

    static String normalizeOptionalKeyword(String keyword) {
        if (keyword == null) {
            return null;
        }
        String trimmedKeyword = keyword.trim();
        return trimmedKeyword.isEmpty() ? null : truncate(trimmedKeyword, MAX_SEARCH_KEYWORD_LENGTH);
    }

    static String normalizeRequiredKeyword(String keyword) {
        String normalizedKeyword = normalizeOptionalKeyword(keyword);
        if (normalizedKeyword == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return normalizedKeyword;
    }

    static String normalizeRequiredTag(String tag) {
        String normalizedTag = normalizeOptionalKeyword(tag);
        if (normalizedTag == null) {
            throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
        }
        return truncate(normalizedTag, MAX_TAG_LENGTH);
    }

    static String normalizeOption(String option) {
        if (option == null) {
            return null;
        }
        String trimmedOption = option.trim();
        return trimmedOption.isEmpty() ? null : truncate(trimmedOption, MAX_OPTION_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value == null || value.length() <= maxLength) {
            return value;
        }
        int endIndex = maxLength;
        if (Character.isHighSurrogate(value.charAt(endIndex - 1))) {
            endIndex--;
        }
        return value.substring(0, endIndex);
    }
}
