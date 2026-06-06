package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

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

    public static Pageable of(Pageable pageable, int defaultPageSize, Sort defaultSort) {
        if (pageable == null || pageable.isUnpaged()) {
            return of(0, defaultPageSize, defaultSort);
        }
        return of(pageable.getPageNumber(), pageable.getPageSize(), defaultSort);
    }

    public static Pageable of(int page, int size, Sort sort, Sort defaultSort, Set<String> allowedSortProperties) {
        if (page < 0 || size < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return PageRequest.of(
                page,
                Math.min(size, DEFAULT_MAX_PAGE_SIZE),
                normalizeSort(sort, defaultSort, allowedSortProperties));
    }

    public static Pageable of(Pageable pageable, int defaultPageSize, Sort defaultSort,
            Set<String> allowedSortProperties) {
        if (pageable == null || pageable.isUnpaged()) {
            return of(0, defaultPageSize, defaultSort, defaultSort, allowedSortProperties);
        }
        return of(
                pageable.getPageNumber(),
                pageable.getPageSize(),
                pageable.getSort(),
                defaultSort,
                allowedSortProperties);
    }

    private static Sort normalizeSort(Sort sort, Sort defaultSort, Set<String> allowedSortProperties) {
        Sort fallbackSort = defaultSort != null ? defaultSort : Sort.unsorted();
        if (sort == null || sort.isUnsorted()) {
            return fallbackSort;
        }
        if (allowedSortProperties == null || allowedSortProperties.isEmpty()) {
            return sort;
        }

        List<Sort.Order> allowedOrders = sort.stream()
                .filter(order -> allowedSortProperties.contains(order.getProperty()))
                .toList();
        if (allowedOrders.isEmpty()) {
            return fallbackSort;
        }

        List<Sort.Order> normalizedOrders = new ArrayList<>(allowedOrders);
        fallbackSort.stream()
                .filter(order -> normalizedOrders.stream()
                        .noneMatch(normalizedOrder -> normalizedOrder.getProperty().equals(order.getProperty())))
                .forEach(normalizedOrders::add);
        return Sort.by(normalizedOrders);
    }
}
