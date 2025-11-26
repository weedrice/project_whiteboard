package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.CodeDetailRequest;
import com.weedrice.whiteboard.global.common.dto.CodeResponse;
import com.weedrice.whiteboard.global.common.dto.CodeTypeCreateRequest;
import com.weedrice.whiteboard.global.common.entity.CommonCodeDetail;
import com.weedrice.whiteboard.global.common.service.CommonCodeService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class CommonCodeController {

    private final CommonCodeService commonCodeService;

    @GetMapping("/codes/{typeCode}")
    public ApiResponse<List<CodeResponse>> getCodes(@PathVariable String typeCode) {
        List<CommonCodeDetail> codes = commonCodeService.getCommonCodes(typeCode);
        List<CodeResponse> response = codes.stream()
                .map(CodeResponse::new)
                .collect(Collectors.toList());
        return ApiResponse.success(response);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/codes")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> createCodeType(@Valid @RequestBody CodeTypeCreateRequest request) {
        commonCodeService.createCodeType(request);
        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PostMapping("/admin/codes/{typeCode}")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Void> createCodeDetail(@PathVariable String typeCode, @Valid @RequestBody CodeDetailRequest request) {
        commonCodeService.createCodeDetail(typeCode, request);
        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @PutMapping("/admin/codes/details/{detailId}")
    public ApiResponse<Void> updateCodeDetail(@PathVariable Long detailId, @Valid @RequestBody CodeDetailRequest request) {
        commonCodeService.updateCodeDetail(detailId, request);
        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('ADMIN')")
    @DeleteMapping("/admin/codes/details/{detailId}")
    public ApiResponse<Void> deleteCodeDetail(@PathVariable Long detailId) {
        commonCodeService.deleteCodeDetail(detailId);
        return ApiResponse.success(null);
    }
}
