package com.weedrice.whiteboard.domain.common.controller;

import com.weedrice.whiteboard.domain.common.dto.*;
import com.weedrice.whiteboard.domain.common.service.CommonCodeService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/common-codes")
@RequiredArgsConstructor
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    // --- Common Code (Master) ---

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommonCodeResponse> createCommonCode(@Valid @RequestBody CommonCodeRequest request) {
        return ApiResponse.success(commonCodeService.createCommonCode(request));
    }

    @GetMapping
    public ApiResponse<List<CommonCodeResponse>> getAllCommonCodes() {
        return ApiResponse.success(commonCodeService.getAllCommonCodes());
    }

    @GetMapping("/{typeCode}")
    public ApiResponse<CommonCodeResponse> getCommonCode(@PathVariable String typeCode) {
        return ApiResponse.success(commonCodeService.getCommonCode(typeCode));
    }

    @PutMapping("/{typeCode}")
    public ApiResponse<CommonCodeResponse> updateCommonCode(
            @PathVariable String typeCode,
            @Valid @RequestBody CommonCodeRequest request) {
        return ApiResponse.success(commonCodeService.updateCommonCode(typeCode, request));
    }

    // --- Common Code Detail ---

    @PostMapping("/{typeCode}/details")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<CommonCodeDetailResponse> createCommonCodeDetail(
            @PathVariable String typeCode,
            @Valid @RequestBody CommonCodeDetailRequest request) {
        return ApiResponse.success(commonCodeService.createCommonCodeDetail(typeCode, request));
    }

    @GetMapping("/{typeCode}/details")
    public ApiResponse<List<CommonCodeDetailResponse>> getCommonCodeDetails(@PathVariable String typeCode) {
        return ApiResponse.success(commonCodeService.getCommonCodeDetails(typeCode));
    }

    @PutMapping("/details/{detailId}")
    public ApiResponse<CommonCodeDetailResponse> updateCommonCodeDetail(
            @PathVariable Long detailId,
            @Valid @RequestBody CommonCodeDetailRequest request) {
        return ApiResponse.success(commonCodeService.updateCommonCodeDetail(detailId, request));
    }

    @DeleteMapping("/details/{detailId}")
    public ApiResponse<Void> deleteCommonCodeDetail(@PathVariable Long detailId) {
        commonCodeService.deleteCommonCodeDetail(detailId);
        return ApiResponse.success(null);
    }
}
