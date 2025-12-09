package com.weedrice.whiteboard.domain.sanction.controller;

import com.weedrice.whiteboard.domain.sanction.dto.SanctionCreateRequest;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionResponse;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/sanctions")
@RequiredArgsConstructor
public class SanctionController {

    private final SanctionService sanctionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createSanction(
            @Valid @RequestBody SanctionCreateRequest request,
            Authentication authentication) {
        Long adminUserId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        return ApiResponse.success(sanctionService.createSanction(
                adminUserId,
                request.getTargetUserId(),
                request.getType(),
                request.getRemark(),
                request.getEndDate()).getSanctionId());
    }

    @GetMapping
    public ApiResponse<org.springframework.data.domain.Page<SanctionResponse>> getSanctions(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(sanctionService.getSanctions(userId, pageable).map(SanctionResponse::from));
    }
}
