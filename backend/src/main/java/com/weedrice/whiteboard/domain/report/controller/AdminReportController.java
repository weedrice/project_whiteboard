package com.weedrice.whiteboard.domain.report.controller;

import com.weedrice.whiteboard.domain.report.dto.ReportProcessRequest;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.service.ReportService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public ApiResponse<PageResponse<ReportResponse>> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponse.success(new PageResponse<>(reportService.getReports(status, targetType, pageable)));
    }

    @PutMapping("/{reportId}")
    public ApiResponse<ReportResponse> processReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportProcessRequest request) {
        Long adminUserId = SecurityUtils.getCurrentUserId();
        ReportResponse response = reportService.processReport(
                adminUserId,
                reportId,
                request.getStatus().name(),
                request.getRemark());
        return ApiResponse.success(response);
    }
}
