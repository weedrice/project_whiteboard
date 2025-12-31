package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.entity.GlobalConfig;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
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
    public ApiResponse<List<GlobalConfig>> getAllConfigs() {
        return ApiResponse.success(globalConfigService.getAllConfigs());
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
    public ApiResponse<GlobalConfig> createConfig(@RequestBody Map<String, String> request) {
        return ApiResponse.success(
                globalConfigService.createConfig(request.get("key"), request.get("value"), request.get("description")));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/configs")
    public ApiResponse<Void> updateConfig(@RequestBody Map<String, String> request) {
        globalConfigService.updateConfig(request.get("key"), request.get("value"), request.get("description"));
        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/configs/{key}")
    public ApiResponse<Void> updateConfigByKey(@PathVariable String key, @RequestBody Map<String, String> request) {
        globalConfigService.updateConfig(key, request.get("value"), request.get("description"));

        return ApiResponse.success(null);
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/admin/configs/{key}")
    public ApiResponse<Void> deleteConfig(@PathVariable String key) {
        globalConfigService.deleteConfig(key);

        return ApiResponse.success(null);
    }
}
