package com.weedrice.whiteboard.domain.moderation.controller;

import com.weedrice.whiteboard.domain.moderation.dto.ModerationAuditLogResponse;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.LocalDate;

@RestController
@RequestMapping("/api/v1/admin/moderation-audits")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class AdminModerationAuditController {

    private final ModerationAuditLogService moderationAuditLogService;

    @GetMapping
    public ApiResponse<PageResponse<ModerationAuditLogResponse>> getModerationAudits(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @RequestParam(required = false) String action,
            @RequestParam(required = false) String actorType,
            @RequestParam(required = false) Long boardId,
            @RequestParam(required = false) String boardUrl,
            @RequestParam(required = false) String boardName,
            @RequestParam(required = false) Long actorUserId,
            @RequestParam(required = false) String actorName,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Sort sort) {
        Sort effectiveSort = sort != null && sort.isSorted()
                ? sort
                : Sort.by(Sort.Direction.DESC, "createdAt");
        Pageable pageable = PageRequestUtils.of(page, size, effectiveSort);
        return ApiResponses.page(moderationAuditLogService.getAdminAudits(
                action,
                actorType,
                boardId,
                boardUrl,
                actorUserId,
                boardName,
                actorName,
                startDate,
                endDate,
                pageable));
    }
}
