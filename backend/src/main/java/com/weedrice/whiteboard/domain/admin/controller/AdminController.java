package com.weedrice.whiteboard.domain.admin.controller;

import com.weedrice.whiteboard.domain.admin.dto.*;
import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.entity.IpBlock;
import com.weedrice.whiteboard.domain.admin.service.AdminService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
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
@PreAuthorize("hasRole('SUPER_ADMIN')")
public class AdminController {

    private final AdminService adminService;

    /**
     * Super Admin 조회
     * @return {@link SuperAdminResponse} Super Admin List
     */
    @GetMapping("/super")
    public ApiResponse<List<SuperAdminResponse>> getSuperAdmin() {
        return ApiResponse.success(adminService.getSuperAdmin());
    }

    /**
     * Super Admin 등록<p>
     * {@link BusinessException} 등록 사용자 없을 시 <code>USER_NOT_FOUND</code><br>
     * 중복 등록 시 <code>DUPLICATE_RESOURCE</code> 반환
     * @param request {@link SuperAdminRequest} 등록 대상 사용자의 loginId를 포함하고있는 객체
     * @return {@link SuperAdminUpdateResponse} 등록된 Super Admin 정보
     */
    @PutMapping("/super/active")
    public ApiResponse<SuperAdminUpdateResponse> activeSuperAdmin(@Valid @RequestBody SuperAdminRequest request) {
        User user = adminService.createSuperAdmin(request.getLoginId());
        return ApiResponse.success(SuperAdminUpdateResponse.from(user));
    }

    /**
     * Super Admin 해제<p>
     * {@link BusinessException} 등록 사용자 없을 시 <code>USER_NOT_FOUND</code><br>
     * 미 등록 사용자 해제 시 <code>INVALID_TARGET</code> 반환
     * @param request {@link SuperAdminRequest} 등록 대상 사용자의 loginId를 포함하고있는 객체
     * @return {@link SuperAdminUpdateResponse} 등록된 Super Admin 정보
     */
    @PutMapping("/super/deactive")
    public ApiResponse<SuperAdminUpdateResponse> deactiveSuperAdmin(@Valid @RequestBody SuperAdminRequest request) {
        User user = adminService.deactiveSuperAdmin(request.getLoginId());
        return ApiResponse.success(SuperAdminUpdateResponse.from(user));
    }

    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminResponse> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        Admin admin = adminService.createAdmin(request.getLoginId(), request.getBoardId(), request.getRole());
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
        IpBlock ipBlock = adminService.blockIp(adminUserId, request.getIpAddress(), request.getReason(),
                request.getEndDate());
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
