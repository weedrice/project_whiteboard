package com.weedrice.whiteboard.domain.point.controller;

import com.weedrice.whiteboard.domain.point.dto.PointHistoryResponse;
import com.weedrice.whiteboard.domain.point.dto.UserPointResponse;
import com.weedrice.whiteboard.domain.point.service.PointService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/points")
@RequiredArgsConstructor
public class PointController {

    private final PointService pointService;

    @GetMapping("/me")
    public ApiResponse<UserPointResponse> getMyPoints() {
        Long userId = SecurityUtils.getCurrentUserId();
        return ApiResponse.success(pointService.getUserPoint(userId));
    }

    @GetMapping("/me/history")
    public ApiResponse<PointHistoryResponse> getMyPointHistories(
            @RequestParam(required = false) String type,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Long userId = SecurityUtils.getCurrentUserId();
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponse.success(pointService.getPointHistories(userId, type, pageable));
    }
}
