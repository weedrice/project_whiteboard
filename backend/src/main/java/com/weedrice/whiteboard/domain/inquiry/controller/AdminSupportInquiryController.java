package com.weedrice.whiteboard.domain.inquiry.controller;

import com.weedrice.whiteboard.domain.inquiry.dto.*;
import com.weedrice.whiteboard.domain.inquiry.entity.*;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryCommandService;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryReadService;
import com.weedrice.whiteboard.global.common.*;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;

@RestController
@RequestMapping("/api/v1/admin/support/inquiries")
@RequiredArgsConstructor
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminSupportInquiryController {
    private final InquiryCommandService commandService;
    private final InquiryReadService readService;

    @GetMapping
    public ApiResponse<PageResponse<InquirySummaryResponse>> getPage(
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) InquiryCategory category,
            @RequestParam(required = false) InquiryPriority priority,
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime from,
            @RequestParam(required = false) @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) LocalDateTime to,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponses.page(readService.getAdminPage(status, category, priority, keyword, from, to, pageable));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryDetailResponse> getDetail(@PathVariable Long inquiryId) {
        return ApiResponse.success(readService.getAdminDetail(inquiryId));
    }

    @PostMapping("/{inquiryId}/start")
    public ApiResponse<InquiryDetailResponse> start(@CurrentUserId Long adminUserId, @PathVariable Long inquiryId) {
        return ApiResponse.success(commandService.start(adminUserId, inquiryId));
    }

    @PostMapping("/{inquiryId}/reply")
    public ApiResponse<InquiryDetailResponse> reply(@CurrentUserId Long adminUserId, @PathVariable Long inquiryId,
                                                    @Valid @RequestBody InquiryMessageCreateRequest request) {
        return ApiResponse.success(commandService.reply(adminUserId, inquiryId, request));
    }

    @PostMapping("/{inquiryId}/notes")
    public ApiResponse<InquiryDetailResponse> note(@CurrentUserId Long adminUserId, @PathVariable Long inquiryId,
                                                   @Valid @RequestBody InquiryMessageCreateRequest request) {
        return ApiResponse.success(commandService.addInternalNote(adminUserId, inquiryId, request));
    }

    @PostMapping("/{inquiryId}/close")
    public ApiResponse<InquiryDetailResponse> close(@CurrentUserId Long adminUserId, @PathVariable Long inquiryId,
                                                    @Valid @RequestBody AdminInquiryCloseRequest request) {
        return ApiResponse.success(commandService.closeByAdmin(adminUserId, inquiryId, request));
    }

    @PostMapping("/{inquiryId}/reopen")
    public ApiResponse<InquiryDetailResponse> reopen(@CurrentUserId Long adminUserId, @PathVariable Long inquiryId) {
        return ApiResponse.success(commandService.reopenByAdmin(adminUserId, inquiryId));
    }
}
