package com.weedrice.whiteboard.global.common;

import com.weedrice.whiteboard.global.common.dto.PageResponse;
import org.springframework.data.domain.Page;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;

public final class ApiResponses {

    private ApiResponses() {
    }

    public static ApiResponse<Void> ok() {
        return ApiResponse.success();
    }

    public static <T> ApiResponse<T> ok(T data) {
        return ApiResponse.success(data);
    }

    public static <T> ApiResponse<PageResponse<T>> page(Page<T> page) {
        return ApiResponse.success(new PageResponse<>(page));
    }

    public static <T> ResponseEntity<ApiResponse<T>> status(HttpStatus status, T data) {
        return ResponseEntity.status(status).body(ok(data));
    }
}
