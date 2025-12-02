package com.weedrice.whiteboard.domain.report.controller;

import com.weedrice.whiteboard.domain.report.dto.ReportProcessRequest;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.service.ReportService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/reports")
@RequiredArgsConstructor
public class AdminReportController {

    private final ReportService reportService;

    @GetMapping
    public ApiResponse<Page<ReportResponse>> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<Report> reports = reportService.getReports(status, targetType, pageable);
        Page<ReportResponse> response = reports.map(ReportResponse::from);
        return ApiResponse.success(response);
    }

    @PutMapping("/{reportId}")
    public ApiResponse<ReportResponse> processReport(
            @PathVariable Long reportId,
            @RequestBody ReportProcessRequest request,
            Authentication authentication) {
        Long adminUserId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Report report = reportService.processReport(
                adminUserId,
                reportId,
                request.getStatus(),
                request.getRemark()
        );
        return ApiResponse.success(ReportResponse.from(report));
    }
}
