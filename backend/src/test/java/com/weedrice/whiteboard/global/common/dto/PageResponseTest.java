package com.weedrice.whiteboard.global.common.dto;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;

import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class PageResponseTest {

    @Test
    @DisplayName("Page 객체로부터 PageResponse 생성")
    void constructor_fromPage() {
        // given
        List<String> content = Arrays.asList("Item1", "Item2");
        Page<String> page = new PageImpl<>(content, PageRequest.of(0, 10), 2);

        // when
        PageResponse<String> response = new PageResponse<>(page);

        // then
        assertThat(response.getContent()).isEqualTo(content);
        assertThat(response.getPage()).isEqualTo(0);
        assertThat(response.getSize()).isEqualTo(10);
        assertThat(response.getTotalElements()).isEqualTo(2);
        assertThat(response.getTotalPages()).isEqualTo(1);
        assertThat(response.isHasNext()).isFalse();
        assertThat(response.isHasPrevious()).isFalse();
    }
}
