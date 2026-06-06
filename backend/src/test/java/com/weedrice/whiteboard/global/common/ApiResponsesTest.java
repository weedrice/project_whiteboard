package com.weedrice.whiteboard.global.common;

import com.weedrice.whiteboard.global.common.dto.PageResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class ApiResponsesTest {

    @Test
    @DisplayName("데이터 성공 응답을 생성한다")
    void ok_withData() {
        ApiResponse<String> response = ApiResponses.ok("data");

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isEqualTo("data");
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("데이터 없는 성공 응답을 생성한다")
    void ok_withoutData() {
        ApiResponse<Void> response = ApiResponses.ok();

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData()).isNull();
        assertThat(response.getError()).isNull();
    }

    @Test
    @DisplayName("페이지 응답을 공통 PageResponse 형식으로 감싼다")
    void page_wrapsPageResponse() {
        Page<String> page = new PageImpl<>(List.of("first", "second"));

        ApiResponse<PageResponse<String>> response = ApiResponses.page(page);

        assertThat(response.isSuccess()).isTrue();
        assertThat(response.getData().getContent()).containsExactly("first", "second");
        assertThat(response.getData().getTotalElements()).isEqualTo(2);
    }

    @Test
    @DisplayName("지정한 HTTP 상태의 성공 ResponseEntity를 생성한다")
    void status_wrapsResponseEntity() {
        ResponseEntity<ApiResponse<String>> response = ApiResponses.status(HttpStatus.CREATED, "created");

        assertThat(response.getStatusCode()).isEqualTo(HttpStatus.CREATED);
        assertThat(response.getBody()).isNotNull();
        assertThat(response.getBody().isSuccess()).isTrue();
        assertThat(response.getBody().getData()).isEqualTo("created");
    }
}
