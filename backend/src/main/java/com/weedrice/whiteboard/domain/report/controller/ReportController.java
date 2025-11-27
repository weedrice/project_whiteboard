package com.weedrice.whiteboard.domain.report.controller;

import com.weedrice.whiteboard.domain.report.dto.ReportCreateRequest;
import com.weedrice.whiteboard.domain.report.dto.ReportProcessRequest;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.service.ReportService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/reports")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createReport(
            @Valid @RequestBody ReportCreateRequest request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Report report = reportService.createReport(
                reporterId,
                request.getTargetType(),
                request.getTargetId(),
                request.getReasonType(),
                request.getRemark(),
                request.getContents());
        return ApiResponse.success(report.getReportId());
    }

    @GetMapping("/reports/me")
    public ApiResponse<ReportResponse> getMyReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(ReportResponse.from(reportService.getMyReports(userId, pageable)));
    }

    @GetMapping("/admin/reports")
    public ApiResponse<ReportResponse> getReports(
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String targetType,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(ReportResponse.from(reportService.getReports(status, targetType, pageable)));
    }

    @PutMapping("/admin/reports/{reportId}")
    public ApiResponse<Long> processReport(
            @PathVariable Long reportId,
            @Valid @RequestBody ReportProcessRequest request,
            Authentication authentication) {
        Long adminUserId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Report report = reportService.processReport(
                adminUserId,
                reportId,
                request.getStatus(),
                request.getRemark());
        return ApiResponse.success(report.getReportId());
    }
}
