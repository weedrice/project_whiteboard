package com.weedrice.whiteboard.domain.report.controller;

import com.weedrice.whiteboard.domain.report.dto.CommentReportRequest;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.PostReportRequest;
import com.weedrice.whiteboard.domain.report.dto.ReportCreateRequest;
import com.weedrice.whiteboard.domain.report.dto.UserReportRequest;
import com.weedrice.whiteboard.domain.report.service.ReportService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/reports")
@RequiredArgsConstructor
public class ReportController {

    private final ReportService reportService;

    @PostMapping("/users")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportUser(
            @Valid @RequestBody UserReportRequest request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Long reportId = reportService.createReport(
                reporterId,
                "USER",
                request.getTargetUserId(),
                normalizeReasonType(request.getReasonType()),
                request.getLink(),
                request.getReason());
        return ApiResponse.success(reportId);
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportPost(
            @Valid @RequestBody PostReportRequest request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Long reportId = reportService.createReport(
                reporterId,
                "POST",
                request.getTargetPostId(),
                normalizeReasonType(request.getReasonType()),
                null,
                request.getReason());
        return ApiResponse.success(reportId);
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportComment(
            @Valid @RequestBody CommentReportRequest request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Long reportId = reportService.createReport(
                reporterId,
                "COMMENT",
                request.getTargetCommentId(),
                normalizeReasonType(request.getReasonType()),
                null,
                request.getReason());
        return ApiResponse.success(reportId);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createReport(
            @Valid @RequestBody ReportCreateRequest request,
            Authentication authentication) {
        Long reporterId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Long reportId = reportService.createReport(
                reporterId,
                request.getTargetType(),
                request.getTargetId(),
                request.getReasonType(),
                request.getRemark(),
                request.getContents());
        return ApiResponse.success(reportId);
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<MyReportResponse>> getMyReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        Pageable pageable = PageRequest.of(page, size);
        return ApiResponse.success(new PageResponse<>(reportService.getMyReports(userId, pageable)));
    }

    private String normalizeReasonType(String reasonType) {
        return reasonType == null || reasonType.isBlank() ? "ETC" : reasonType;
    }
}
