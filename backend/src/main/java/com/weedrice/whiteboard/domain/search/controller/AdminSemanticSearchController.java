package com.weedrice.whiteboard.domain.search.controller;

import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchBackfillResponse;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchBackfillService;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/search/semantic")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class AdminSemanticSearchController {

    private final SemanticSearchBackfillService backfillService;

    @PostMapping("/backfill")
    public ApiResponse<SemanticSearchBackfillResponse> enqueueBackfill(
            @RequestParam(defaultValue = "ALL") String contentType,
            @RequestParam(defaultValue = "100") int limit) {
        int enqueuedCount = backfillService.enqueueBackfill(contentType, limit);
        return ApiResponse.success(SemanticSearchBackfillResponse.builder()
                .enqueuedCount(enqueuedCount)
                .build());
    }
}
