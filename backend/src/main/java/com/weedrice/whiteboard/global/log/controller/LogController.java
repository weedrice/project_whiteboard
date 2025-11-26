package com.weedrice.whiteboard.global.log.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.log.dto.LogResponse;
import com.weedrice.whiteboard.global.log.service.LogService;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/admin/logs")
@RequiredArgsConstructor
public class LogController {

    private final LogService logService;

    @PreAuthorize("hasRole('ADMIN')")
    @GetMapping
    public ApiResponse<LogResponse> getLogs(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        Pageable pageable = PageRequest.of(page, size, Sort.by("createdAt").descending());
        return ApiResponse.success(LogResponse.from(logService.getLogs(pageable)));
    }
}
