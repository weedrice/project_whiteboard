package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class GlobalConfigController {

    private final GlobalConfigService globalConfigService;

    @GetMapping("/configs/{key}")
    public ApiResponse<Map<String, String>> getConfig(@PathVariable String key) {
        String value = globalConfigService.getConfig(key);
        return ApiResponse.success(Collections.singletonMap(key, value));
    }

    @PutMapping("/admin/configs/{key}")
    public ApiResponse<Void> updateConfig(@PathVariable String key, @RequestBody Map<String, String> request) {
        globalConfigService.updateConfig(key, request.get("value"));
        return ApiResponse.success(null);
    }
}