package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigCreateRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateByKeyRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateRequest;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class GlobalConfigController {

    private final GlobalConfigService globalConfigService;

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/configs/{key}")
    public ApiResponse<GlobalConfigResponse> getConfig(@PathVariable String key) {
        return ApiResponse.success(globalConfigService.getConfigResponseOrThrow(key));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @GetMapping("/admin/configs")
    public ApiResponse<List<GlobalConfigResponse>> getAllConfigs() {
        return ApiResponse.success(globalConfigService.getAllConfigs());
    }

    @GetMapping("/configs/public")
    public ApiResponse<List<GlobalConfigResponse>> getPublicConfigs() {
        return ApiResponse.success(globalConfigService.getPublicConfigs());
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PostMapping("/admin/configs")
    public ApiResponse<GlobalConfigResponse> createConfig(@Valid @RequestBody GlobalConfigCreateRequest request) {
        return ApiResponse.success(globalConfigService.createConfig(
                request.getKey(),
                request.getValue(),
                request.getDescription()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/configs")
    public ApiResponse<GlobalConfigResponse> updateConfig(@Valid @RequestBody GlobalConfigUpdateRequest request) {
        return ApiResponse.success(globalConfigService.updateConfig(
                request.getKey(),
                request.getValue(),
                request.getDescription()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @PutMapping("/admin/configs/{key}")
    public ApiResponse<GlobalConfigResponse> updateConfigByKey(
            @PathVariable String key,
            @Valid @RequestBody GlobalConfigUpdateByKeyRequest request) {
        return ApiResponse.success(globalConfigService.updateConfig(key, request.getValue(), request.getDescription()));
    }

    @PreAuthorize("hasRole('SUPER_ADMIN')")
    @DeleteMapping("/admin/configs/{key}")
    public ApiResponse<Void> deleteConfig(@PathVariable String key) {
        globalConfigService.deleteConfig(key);
        return ApiResponse.success(null);
    }
}
