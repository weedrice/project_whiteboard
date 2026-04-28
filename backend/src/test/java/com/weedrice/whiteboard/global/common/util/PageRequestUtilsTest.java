package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;

import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.jupiter.api.Assertions.assertThrows;

class PageRequestUtilsTest {

    @Test
    @DisplayName("페이지 번호는 0 이상이어야 한다")
    void of_rejectsNegativePage() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> PageRequestUtils.of(-1, 20));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("페이지 크기는 1 이상이어야 한다")
    void of_rejectsNonPositiveSize() {
        BusinessException exception = assertThrows(BusinessException.class,
                () -> PageRequestUtils.of(0, 0));

        assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("페이지 크기는 최대 100으로 제한한다")
    void of_clampsLargeSize() {
        Pageable pageable = PageRequestUtils.of(2, 1000);

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
    }

    @Test
    @DisplayName("정렬 조건은 보존한다")
    void of_preservesSort() {
        Sort sort = Sort.by(Sort.Direction.DESC, "createdAt");

        Pageable pageable = PageRequestUtils.of(1, 20, sort);

        assertThat(pageable.getSort()).isEqualTo(sort);
    }

    @Test
    @DisplayName("허용된 정렬 필드만 보존하고 페이지 크기는 상한으로 제한한다")
    void of_filtersSortByAllowedProperties() {
        Sort sort = Sort.by(Sort.Order.asc("createdAt"), Sort.Order.desc("title"));
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"));

        Pageable pageable = PageRequestUtils.of(2, 1000, sort, defaultSort, Set.of("createdAt"));

        assertThat(pageable.getPageNumber()).isEqualTo(2);
        assertThat(pageable.getPageSize()).isEqualTo(100);
        assertThat(pageable.getSort()).isEqualTo(Sort.by(Sort.Order.asc("createdAt")));
    }

    @Test
    @DisplayName("허용된 정렬 필드가 없으면 기본 정렬을 사용한다")
    void of_usesDefaultSortWhenNoAllowedSortRemains() {
        Sort defaultSort = Sort.by(Sort.Order.desc("createdAt"), Sort.Order.desc("postId"));

        Pageable pageable = PageRequestUtils.of(0, 20, Sort.by("title"), defaultSort, Set.of("createdAt", "postId"));

        assertThat(pageable.getSort()).isEqualTo(defaultSort);
    }
}
