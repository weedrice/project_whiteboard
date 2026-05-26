package com.weedrice.whiteboard.global.common.controller;

import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigCreateRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigResponse;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateByKeyRequest;
import com.weedrice.whiteboard.global.common.dto.GlobalConfigUpdateRequest;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/v1")
public class GlobalConfigController {

    private final GlobalConfigService globalConfigService;

    @GetMapping("/configs/{key}")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> getConfig(
            @PathVariable String key,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(globalConfigService.getConfigResponseOrThrow(requiredUserId(userDetails), key));
    }

    @GetMapping("/admin/configs")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<List<GlobalConfigResponse>> getAllConfigs(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(globalConfigService.getAllConfigs(requiredUserId(userDetails)));
    }

    @GetMapping("/configs/public")
    public ApiResponse<List<GlobalConfigResponse>> getPublicConfigs() {
        return ApiResponse.success(globalConfigService.getPublicConfigs());
    }

    @PostMapping("/admin/configs")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> createConfig(
            @Valid @RequestBody GlobalConfigCreateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(globalConfigService.createConfig(
                requiredUserId(userDetails),
                request.getKey(),
                request.getValue(),
                request.getDescription()));
    }

    @PutMapping("/admin/configs")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> updateConfig(
            @Valid @RequestBody GlobalConfigUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(globalConfigService.updateConfig(
                requiredUserId(userDetails),
                request.getKey(),
                request.getValue(),
                request.getDescription()));
    }

    @PutMapping("/admin/configs/{key}")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<GlobalConfigResponse> updateConfigByKey(
            @PathVariable String key,
            @Valid @RequestBody GlobalConfigUpdateByKeyRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        return ApiResponse.success(globalConfigService.updateConfig(
                requiredUserId(userDetails),
                key,
                request.getValue(),
                request.getDescription()));
    }

    @DeleteMapping("/admin/configs/{key}")
    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public ApiResponse<Void> deleteConfig(
            @PathVariable String key,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        globalConfigService.deleteConfig(requiredUserId(userDetails), key);
        return ApiResponse.success(null);
    }
}
