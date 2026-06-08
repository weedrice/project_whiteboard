package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestUtilsTest {

    @Test
    @DisplayName("rejects negative page")
    void of_rejectsNegativePage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> PageRequestUtils.of(-1, 20));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("rejects non-positive size")
    void of_rejectsNonPositiveSize() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> PageRequestUtils.of(0, 0));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("clamps large size")
    void of_clampsLargeSize() {
        Pageable pageable = PageRequestUtils.of(2, 1000);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("preserves sort")
    void of_preservesSort() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable pageable = PageRequestUtils.of(1, 20, sort);

        assertThat(pageable.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("pageable overload uses default page and sort when unpaged")
    void of_pageableOverloadUsesDefaultWhenUnpaged() {
        Sort sort = Sort.by(Sort.Order.desc("createdAt"));

        Pageable pageable = PageRequestUtils.of(Pageable.unpaged(), 20, sort);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("pageable overload preserves requested page and size with server sort")
    void of_pageableOverloadPreservesPageAndSizeWithServerSort() {
        Sort requestedSort = Sort.by(Sort.Order.asc("title"));
        Sort serverSort = Sort.by(Sort.Order.desc("createdAt"));
        Pageable requested = PageRequest.of(2, 250, requestedSort);

        Pageable pageable = PageRequestUtils.of(requested, 20, serverSort);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort()).isEqualTo(serverSort);
    }

    @Test
    @DisplayName("pageable overload without sort uses unsorted default")
    void of_pageableOverloadWithoutSortUsesUnsortedDefault() {
        Pageable pageable = PageRequestUtils.of(Pageable.unpaged(), 20);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort()).isEqualTo(Sort.unsorted());
    }

    @Test
    @DisplayName("bounded pageable uses default size for unpaged requests")
    void bounded_usesDefaultSizeForUnpagedRequests() {
        Pageable pageable = PageRequestUtils.bounded(Pageable.unpaged(), 20, 50);

        assertThat(pageable.getPageNumber()).isZero();
        assertThat(pageable.getPageSize()).isEqualTo(20);
        assertThat(pageable.getSort().isUnsorted()).isTrue();
    }

    @Test
    @DisplayName("bounded pageable clamps requested size to max")
    void bounded_clampsRequestedSizeToMax() {
        Pageable pageable = PageRequestUtils.bounded(PageRequest.of(2, 250), 20, 50);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(50);
    }

    @Test
    @DisplayName("bounded pageable keeps only allowed requested sort orders")
    void bounded_keepsOnlyAllowedRequestedSortOrders() {
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));
        Pageable requested = PageRequest.of(0, 20,
                Sort.by(Sort.Order.asc("createdAt"), Sort.Order.desc("title")));

        Pageable pageable = PageRequestUtils.bounded(requested, 20, defaultSort, Set.of("createdAt", "postId"));

        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("createdAt")));
    }

    @Test
    @DisplayName("bounded pageable uses default sort when no allowed sort remains")
    void bounded_usesDefaultSortWhenNoAllowedSortRemains() {
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));
        Pageable requested = PageRequest.of(0, 20, Sort.by(Sort.Order.asc("title")));

        Pageable pageable = PageRequestUtils.bounded(requested, 20, defaultSort, Set.of("createdAt", "postId"));

        assertThat(pageable.getSort()).isEqualTo(defaultSort);
    }

    @Test
    @DisplayName("filters sort by allowed properties")
    void of_filtersSortByAllowedProperties() {
        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.desc("title"));
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"));

        Pageable pageable = PageRequestUtils.of(2, 1000, sort, defaultSort, Set.of("createdAt"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("createdAt")));
    }

    @Test
    @DisplayName("appends missing default tie breaker sort")
    void of_appendsMissingDefaultTieBreakerSort() {
        Sort sort = Sort.by(Sort.Order.asc("createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

        Pageable pageable = PageRequestUtils.of(0, 20, sort, defaultSort, Set.of("createdAt", "postId"));

        assertThat(pageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.desc("postId")));
    }

    @Test
    @DisplayName("does not duplicate requested default sort properties")
    void of_doesNotDuplicateRequestedDefaultSortProperties() {
        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.asc("postId"));
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

        Pageable pageable = PageRequestUtils.of(0, 20, sort, defaultSort, Set.of("createdAt", "postId"));

        assertThat(pageable.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("preserves server default tie breaker outside allowed sort properties")
    void of_preservesServerDefaultTieBreakerOutsideAllowedSortProperties() {
        Sort sort = Sort.by(Sort.Order.asc("createdAt"));
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("notificationId"));

        Pageable pageable = PageRequestUtils.of(0, 20, sort, defaultSort, Set.of("createdAt"));

        assertThat(pageable.getSort()).isEqualTo(Sort.by(
                Sort.Order.asc("createdAt"),
                Sort.Order.desc("notificationId")));
    }

    @Test
    @DisplayName("uses default sort when no allowed sort remains")
    void of_usesDefaultSortWhenNoAllowedSortRemains() {
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

        Pageable pageable = PageRequestUtils.of(0, 20, Sort.by("title"), defaultSort, Set.of("createdAt", "postId"));

        assertThat(pageable.getSort()).isEqualTo(defaultSort);
    }
}
