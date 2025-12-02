package com.weedrice.whiteboard.domain.admin.controller;

import com.weedrice.whiteboard.domain.admin.dto.AdminCreateRequest;
import com.weedrice.whiteboard.domain.admin.dto.AdminResponse;
import com.weedrice.whiteboard.domain.admin.dto.IpBlockRequest;
import com.weedrice.whiteboard.domain.admin.dto.IpBlockResponse;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.service.AdminService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;

@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
public class AdminController {

    private final AdminService adminService;

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminResponse> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        Admin admin = adminService.createAdmin(request.getUserId(), request.getBoardId(), request.getRole());
        return ApiResponse.success(AdminResponse.from(admin));
    }

    @GetMapping("/admins")
    public ApiResponse<List<AdminResponse>> getAllAdmins() {
        List<Admin> admins = adminService.getAllAdmins();
        return ApiResponse.success(AdminResponse.from(admins));
    }

    @PutMapping("/admins/{adminId}/deactivate")
    public ApiResponse<Void> deactivateAdmin(@PathVariable Long adminId) {
        adminService.deactivateAdmin(adminId);
        return ApiResponse.success(null);
    }

    @PutMapping("/admins/{adminId}/activate")
    public ApiResponse<Void> activateAdmin(@PathVariable Long adminId) {
        adminService.activateAdmin(adminId);
        return ApiResponse.success(null);
    }

    @PostMapping("/ip-blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IpBlockResponse> blockIp(
            @Valid @RequestBody IpBlockRequest request,
            Authentication authentication) {
        Long adminUserId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        IpBlock ipBlock = adminService.blockIp(adminUserId, request.getIpAddress(), request.getReason(), request.getEndDate());
        return ApiResponse.success(IpBlockResponse.from(ipBlock));
    }

    @DeleteMapping("/ip-blocks/{ipAddress}")
    public ApiResponse<Void> unblockIp(@PathVariable String ipAddress) {
        adminService.unblockIp(ipAddress);
        return ApiResponse.success(null);
    }

    @GetMapping("/ip-blocks")
    public ApiResponse<List<IpBlockResponse>> getBlockedIps() {
        List<IpBlock> ipBlocks = adminService.getBlockedIps();
        return ApiResponse.success(IpBlockResponse.from(ipBlocks));
    }

    @GetMapping("/stats")
    public ApiResponse<com.weedrice.whiteboard.domain.admin.dto.DashboardStatsDto> getDashboardStats() {
        return ApiResponse.success(adminService.getDashboardStats());
    }
}
