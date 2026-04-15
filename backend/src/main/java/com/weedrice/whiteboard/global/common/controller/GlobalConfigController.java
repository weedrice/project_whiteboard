package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigCreateRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateByKeyRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateRequest;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.Collections;
import java.util.List;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class GlobalConfigController {

    private final GlobalConfigService globalConfigService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/configs/{key}")
    public ApiResponse<Map<String, String>> getConfig(@PathVariable String key) {
        String value = globalConfigService.getConfig(key);
        return ApiResponse.success(Collections.singletonMap(key, value));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admin/configs")
    public ApiResponse<List<GlobalConfigResponse>> getAllConfigs() {
        return ApiResponse.success(globalConfigService.getAllConfigs().stream()
                .map(GlobalConfigResponse::from)
                .toList());
    }

    @GetMapping("/configs/public")
    public ApiResponse<Map<String, String>> getPublicConfigs() {
        List<GlobalConfig> configs = globalConfigService.getPublicConfigs();
        Map<String, String> configMap = new java.util.HashMap<>();
        for (GlobalConfig config : configs) {
            configMap.put(config.getConfigKey(), config.getConfigValue());
        }
        return ApiResponse.success(configMap);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/admin/configs")
    public ApiResponse<GlobalConfigResponse> createConfig(@Valid @RequestBody GlobalConfigCreateRequest request) {
        GlobalConfig config = globalConfigService.createConfig(
                request.getKey(),
                request.getValue(),
                request.getDescription());
        return ApiResponse.success(GlobalConfigResponse.from(config));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/configs")
    public ApiResponse<GlobalConfigResponse> updateConfig(@Valid @RequestBody GlobalConfigUpdateRequest request) {
        GlobalConfig config = globalConfigService.updateConfig(
                request.getKey(),
                request.getValue(),
                request.getDescription());
        return ApiResponse.success(GlobalConfigResponse.from(config));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/configs/{key}")
    public ApiResponse<GlobalConfigResponse> updateConfigByKey(
            @PathVariable String key,
            @Valid @RequestBody GlobalConfigUpdateByKeyRequest request) {
        GlobalConfig config = globalConfigService.updateConfig(key, request.getValue(), request.getDescription());
        return ApiResponse.success(GlobalConfigResponse.from(config));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/admin/configs/{key}")
    public ApiResponse<Void> deleteConfig(@PathVariable String key) {
        globalConfigService.deleteConfig(key);
        return ApiResponse.success(null);
    }
}
