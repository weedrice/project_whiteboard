package com.weedrice.whiteboard.domain.post.service;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.validation.NoHtmlValidator;

public final class PostTitleValidator {

    private static final int MAX_TITLE_LENGTH = 200;

    private PostTitleValidator() {
    }

    public static void validate(String title) {
        if (title == null
                || title.isBlank()
                || title.length() > MAX_TITLE_LENGTH
                || NoHtmlValidator.containsUnsafeHtml(title)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
    }
}
