package com.weedrice.whiteboard.domain.report.controller;

import com.weedrice.whiteboard.domain.report.dto.CommentReportRequest;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.PostReportRequest;
import com.weedrice.whiteboard.domain.report.dto.ReportCreateRequest;
import com.weedrice.whiteboard.domain.report.dto.UserReportRequest;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.report.service.ReportService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
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
            @CurrentUserId Long reporterId) {
        return createReportResponse(
                reporterId,
                ReportTargetType.USER.name(),
                request.getTargetUserId(),
                request.getReasonType(),
                request.getLink(),
                request.getReason());
    }

    @PostMapping("/posts")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportPost(
            @Valid @RequestBody PostReportRequest request,
            @CurrentUserId Long reporterId) {
        return createReportResponse(
                reporterId,
                ReportTargetType.POST.name(),
                request.getTargetPostId(),
                request.getReasonType(),
                null,
                request.getReason());
    }

    @PostMapping("/comments")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> reportComment(
            @Valid @RequestBody CommentReportRequest request,
            @CurrentUserId Long reporterId) {
        return createReportResponse(
                reporterId,
                ReportTargetType.COMMENT.name(),
                request.getTargetCommentId(),
                request.getReasonType(),
                null,
                request.getReason());
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<Long> createReport(
            @Valid @RequestBody ReportCreateRequest request,
            @CurrentUserId Long reporterId) {
        return createReportResponse(
                reporterId,
                request.getTargetType().name(),
                request.getTargetId(),
                request.getReasonType().name(),
                request.getRemark(),
                request.getContents());
    }

    private ApiResponse<Long> createReportResponse(
            Long reporterId,
            String targetType,
            Long targetId,
            String reasonType,
            String remark,
            String contents) {
        Long reportId = reportService.createReport(
                reporterId,
                targetType,
                targetId,
                reasonType,
                remark,
                contents);
        return ApiResponse.success(reportId);
    }

    @GetMapping("/me")
    public ApiResponse<PageResponse<MyReportResponse>> getMyReports(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserId Long userId) {
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponses.page(reportService.getMyReports(userId, pageable));
    }
}
