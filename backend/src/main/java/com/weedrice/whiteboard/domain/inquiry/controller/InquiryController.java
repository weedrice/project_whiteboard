package com.weedrice.whiteboard.domain.inquiry.controller;

import com.weedrice.whiteboard.domain.inquiry.dto.*;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryCategory;
import com.weedrice.whiteboard.domain.inquiry.entity.InquiryStatus;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryCommandService;
import com.weedrice.whiteboard.domain.inquiry.service.InquiryReadService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/inquiries")
@RequiredArgsConstructor
public class InquiryController {
    private final InquiryCommandService commandService;
    private final InquiryReadService readService;

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<InquiryDetailResponse> create(@CurrentUserId Long userId,
                                                     @Valid @RequestBody InquiryCreateRequest request) {
        return ApiResponse.success(commandService.create(userId, request));
    }

    @GetMapping
    public ApiResponse<PageResponse<InquirySummaryResponse>> getMine(
            @CurrentUserId Long userId,
            @RequestParam(required = false) InquiryStatus status,
            @RequestParam(required = false) InquiryCategory category,
            @PageableDefault(size = 20) Pageable pageable) {
        return ApiResponses.page(readService.getMine(userId, status, category, pageable));
    }

    @GetMapping("/{inquiryId}")
    public ApiResponse<InquiryDetailResponse> getDetail(@CurrentUserId Long userId,
                                                       @PathVariable Long inquiryId) {
        return ApiResponse.success(readService.getMineDetail(userId, inquiryId));
    }

    @PostMapping("/{inquiryId}/messages")
    public ApiResponse<InquiryDetailResponse> addMessage(@CurrentUserId Long userId,
                                                         @PathVariable Long inquiryId,
                                                         @Valid @RequestBody InquiryMessageCreateRequest request) {
        return ApiResponse.success(commandService.addUserMessage(userId, inquiryId, request));
    }

    @PostMapping("/{inquiryId}/withdraw")
    public ApiResponse<InquiryDetailResponse> withdraw(@CurrentUserId Long userId,
                                                       @PathVariable Long inquiryId) {
        return ApiResponse.success(commandService.withdraw(userId, inquiryId));
    }

    @PostMapping("/{inquiryId}/close")
    public ApiResponse<InquiryDetailResponse> close(@CurrentUserId Long userId,
                                                    @PathVariable Long inquiryId) {
        return ApiResponse.success(commandService.closeByUser(userId, inquiryId));
    }
}
