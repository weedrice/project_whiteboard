package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

public final class PageRequestUtils {

    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PageRequestUtils() {
    }

    public static Pageable of(int page, int size) {
        return of(page, size, Sort.unsorted());
    }

    public static Pageable of(int page, int size, Sort sort) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        Sort safeSort = sort != null ? sort : Sort.unsorted();
        return PageRequest.of(page, Math.min(size, DEFAULT_MAX_PAGE_SIZE), safeSort);
    }
}
