package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigCreateRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateByKeyRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateRequest;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.security.CurrentUserId;
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

    @GetMapping("/configs/{key}")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> getConfig(
            @PathVariable String key,
            @CurrentUserId Long userId) {
        return ApiResponse.success(globalConfigService.getConfigResponseOrThrow(userId, key));
    }

    @GetMapping("/admin/configs")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<List<GlobalConfigResponse>> getAllConfigs(
            @CurrentUserId Long userId) {
        return ApiResponse.success(globalConfigService.getAllConfigs(userId));
    }

    @GetMapping("/configs/public")
    public ApiResponse<List<GlobalConfigResponse>> getPublicConfigs() {
        return ApiResponse.success(globalConfigService.getPublicConfigs());
    }

    @PostMapping("/admin/configs")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> createConfig(
            @Valid @RequestBody GlobalConfigCreateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(globalConfigService.createConfig(
                userId,
                request.getKey(),
                request.getValue(),
                request.getDescription()));
    }

    @PutMapping("/admin/configs")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> updateConfig(
            @Valid @RequestBody GlobalConfigUpdateRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(globalConfigService.updateConfig(
                userId,
                request.getKey(),
                request.getValue(),
                request.getDescription()));
    }

    @PutMapping("/admin/configs/{key}")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> updateConfigByKey(
            @PathVariable String key,
            @Valid @RequestBody GlobalConfigUpdateByKeyRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(globalConfigService.updateConfig(
                userId,
                key,
                request.getValue(),
                request.getDescription()));
    }

    @DeleteMapping("/admin/configs/{key}")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String key,
            @CurrentUserId Long userId) {
        globalConfigService.deleteConfig(userId, key);
        return ApiResponses.ok();
    }
}
