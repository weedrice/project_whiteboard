package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * 페이지 요청을 안전한 범위로 좁힌다.
 *
 * <p><b>오버로드를 고를 때 주의할 점.</b> 같은 자리의 {@code int} 인자가 계열마다 뜻이 다르다.
 *
 * <ul>
 *   <li>{@code of(...)} 계열의 {@code int}는 <b>기본 크기</b>이고, 상한은 항상
 *       {@link #DEFAULT_MAX_PAGE_SIZE}로 고정된다.</li>
 *   <li>{@code bounded(Pageable, int, int, ...)}의 두 번째 인자는 <b>기본 크기</b>,
 *       세 번째가 <b>상한</b>이다.</li>
 *   <li>{@code bounded(Pageable, int, Sort, Set)}의 두 번째 인자는 <b>상한</b>이며
 *       기본 크기로도 함께 쓰인다.</li>
 * </ul>
 *
 * <p>잘못된 입력의 처리도 계열마다 다르다. {@code of(...)}는 {@code size < 1}에
 * {@code VALIDATION_ERROR}를 던지고, {@code bounded(...)}는 1로 올려 보정한다.
 * 두 동작은 이미 배포된 계약이라 통일하지 않았다.
 */
public final class PageRequestUtils {

    public static final int DEFAULT_MAX_PAGE_SIZE = 100;

    private PageRequestUtils() {
    }

    /** 요청 크기는 {@link #DEFAULT_MAX_PAGE_SIZE}로 잘린다. {@code size < 1}이면 예외를 던진다. */
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

    public static Pageable of(Pageable pageable, int defaultPageSize) {
        return of(pageable, defaultPageSize, Sort.unsorted());
    }

    /**
     * @param defaultPageSize {@code pageable}이 없을 때 쓸 기본 크기
     * @param maxPageSize     요청 크기의 상한
     */
    public static Pageable bounded(Pageable pageable, int defaultPageSize, int maxPageSize) {
        return bounded(pageable, defaultPageSize, maxPageSize, Sort.unsorted(), Set.of());
    }

    /**
     * 기본 크기와 상한을 같은 값으로 두는 축약형이다.
     *
     * @param maxPageSize 요청 크기의 상한이자 {@code pageable}이 없을 때의 기본 크기.
     *                    세 인자 오버로드의 두 번째 자리와 뜻이 다르니 주의한다.
     */
    public static Pageable bounded(Pageable pageable, int maxPageSize, Sort defaultSort,
            Set<String> allowedSortProperties) {
        return bounded(pageable, maxPageSize, maxPageSize, defaultSort, allowedSortProperties);
    }

    public static Pageable bounded(Pageable pageable, int defaultPageSize, int maxPageSize, Sort defaultSort,
            Set<String> allowedSortProperties) {
        if (defaultPageSize < 1 || maxPageSize < 1) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        int pageNumber = pageable != null && pageable.isPaged() ? Math.max(pageable.getPageNumber(), 0) : 0;
        int requestedSize = pageable != null && pageable.isPaged() ? pageable.getPageSize() : defaultPageSize;
        int pageSize = Math.min(Math.max(requestedSize, 1), maxPageSize);
        return PageRequest.of(pageNumber, pageSize,
                normalizeBoundedSort(pageable, defaultSort, allowedSortProperties));
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

    private static Sort normalizeBoundedSort(Pageable pageable, Sort defaultSort, Set<String> allowedSortProperties) {
        Sort fallbackSort = defaultSort != null ? defaultSort : Sort.unsorted();
        if (pageable == null || !pageable.isPaged() || pageable.getSort().isUnsorted()
                || allowedSortProperties == null || allowedSortProperties.isEmpty()) {
            return fallbackSort;
        }

        List<Sort.Order> allowedOrders = pageable.getSort().stream()
                .filter(order -> allowedSortProperties.contains(order.getProperty()))
                .toList();
        return allowedOrders.isEmpty() ? fallbackSort : Sort.by(allowedOrders);
    }
}
