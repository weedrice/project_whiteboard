package com.weedrice.whiteboard.domain.moderation.controller;

import com.weedrice.whiteboard.domain.moderation.dto.ModerationAuditLogResponse;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/boards/{boardUrl}/manager/audits")
@RequiredArgsConstructor
public class BoardManagerModerationAuditController {

    private final ModerationAuditLogService moderationAuditLogService;

    @GetMapping
    public ApiResponse<PageResponse<ModerationAuditLogResponse>> getBoardManagerAudits(
            @PathVariable String boardUrl,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Sort sort,
            @CurrentUserId Long managerUserId) {
        Sort effectiveSort = sort != null && sort.isSorted()
                ? sort
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequestUtils.of(page, size, effectiveSort);
        return ApiResponses.page(moderationAuditLogService.getBoardManagerAudits(
                managerUserId,
                boardUrl,
                action,
                actorType,
                actorUserId,
                startDate,
                endDate,
                pageable));
    }
}
