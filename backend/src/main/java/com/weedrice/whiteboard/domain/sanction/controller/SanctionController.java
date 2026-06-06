package com.weedrice.whiteboard.domain.sanction.controller;

import com.weedrice.whiteboard.domain.sanction.dto.SanctionCreateRequest;
import com.weedrice.whiteboard.domain.sanction.dto.SanctionResponse;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

@RestController
@RequestMapping("/api/v1/admin/sanctions")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class SanctionController {

    private final SanctionService sanctionService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createSanction(
            @Valid @RequestBody SanctionCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long adminUserId = requiredUserId(userDetails);
        return ApiResponse.success(sanctionService.createSanction(
                adminUserId,
                request.getTargetUserId(),
                request.getType(),
                request.getRemark(),
                request.getEndDate(),
                request.getContentId(),
                request.getContentType()));
    }

    @GetMapping
    public ApiResponse<PageResponse<SanctionResponse>> getSanctions(
            @RequestParam(required = false) Long userId,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponses.page(sanctionService.getSanctions(userId, pageable));
    }
}
