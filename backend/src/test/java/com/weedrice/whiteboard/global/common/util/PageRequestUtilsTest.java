package com.weedrice.whiteboard.global.common.util;

import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Pageable;

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
}
