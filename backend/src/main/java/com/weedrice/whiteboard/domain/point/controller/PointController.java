package com.weedrice.whiteboard.domain.point.controller;

import com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse;
import com.weedrice.whiteboard.domain.point.dto.UserPointResponse;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping("/me")
    public ApiResponse<UserPointResponse> getMyPoints(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(pointService.getUserPoint(userId));
    }

    @GetMapping("/me/history")
    public ApiResponse<PointHistoryResponse> getMyPointHistories(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponse.success(pointService.getPointHistories(userId, type, pageable));
    }
}
