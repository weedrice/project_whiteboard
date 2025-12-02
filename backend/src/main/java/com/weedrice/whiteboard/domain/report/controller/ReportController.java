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
import java.util.Map;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportUser(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Long targetUserId = Long.valueOf(request.get("targetUserId").toString());
        String reason = (String) request.get("reason");
        // link는 remarks에 포함하거나 별도 처리, 여기선 remarks로 사용
        String link = (String) request.get("link");
        
        Report report = reportService.createReport(
                reporterId,
                "USER",
                targetUserId,
                "ETC", // Default reason type or infer from reason
                reason + (link != null ? " Link: " + link : ""),
                null);
        return ApiResponse.success(report.getReportId());
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportPost(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Long targetPostId = Long.valueOf(request.get("targetPostId").toString());
        String reason = (String) request.get("reason");

        Report report = reportService.createReport(
                reporterId,
                "POST",
                targetPostId,
                "ETC",
                reason,
                null);
        return ApiResponse.success(report.getReportId());
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportComment(
            @RequestBody Map<String, Object> request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Long targetCommentId = Long.valueOf(request.get("targetCommentId").toString());
        String reason = (String) request.get("reason");

        Report report = reportService.createReport(
                reporterId,
                "COMMENT",
                targetCommentId,
                "ETC",
                reason,
                null);
        return ApiResponse.success(report.getReportId());
    }
    
    @PostMapping
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

    @GetMapping("/me")
    public ApiResponse<org.springframework.data.domain.Page<ReportResponse>> getMyReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(reportService.getMyReports(userId, pageable).map(ReportResponse::from));
    }
}