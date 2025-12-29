package com.weedrice.whiteboard.domain.admin.controller;

import com.weedrice.whiteboard.domain.admin.dto.*;

import com.weedrice.whiteboard.domain.admin.service.AdminService;
import com.weedrice.whiteboard.domain.user.entity.Role;

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

/**
 * 관리자 기능을 처리하는 컨트롤러
 */
@RestController
@RequestMapping("/api/v1/admin")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class AdminController {

    private final AdminService adminService;

    /**
     * Super Admin 조회
     * 
     * @return {@link SuperAdminResponse} Super Admin List
     */
    @GetMapping("/super")
    public ApiResponse<List<SuperAdminResponse>> getSuperAdmin() {
        return ApiResponse.success(adminService.getSuperAdmin());
    }

    /**
     * Super Admin 등록
     * <p>
     * {@link BusinessException} 등록 사용자 없을 시 <code>USER_NOT_FOUND</code><br>
     * 중복 등록 시 <code>DUPLICATE_RESOURCE</code> 반환
     * 
     * @param request {@link SuperAdminRequest} 등록 대상 사용자의 loginId를 포함하고있는 객체
     * @return {@link SuperAdminUpdateResponse} 등록된 Super Admin 정보
     */
    @PutMapping("/super/active")
    public ApiResponse<SuperAdminUpdateResponse> activeSuperAdmin(@Valid @RequestBody SuperAdminRequest request) {
        return ApiResponse.success(adminService.createSuperAdmin(request.getLoginId()));
    }

    /**
     * Super Admin 해제
     * <p>
     * {@link BusinessException} 등록 사용자 없을 시 <code>USER_NOT_FOUND</code><br>
     * 미 등록 사용자 해제 시 <code>INVALID_TARGET</code> 반환
     * 
     * @param request {@link SuperAdminRequest} 등록 대상 사용자의 loginId를 포함하고있는 객체
     * @return {@link SuperAdminUpdateResponse} 등록된 Super Admin 정보
     */
    @PutMapping("/super/deactive")
    public ApiResponse<SuperAdminUpdateResponse> deactiveSuperAdmin(@Valid @RequestBody SuperAdminRequest request) {
        return ApiResponse.success(adminService.deactiveSuperAdmin(request.getLoginId()));
    }

    /**
     * Admin 등록
     * 
     * @param request {@link AdminCreateRequest} 등록할 Admin 정보 (loginId, boardId,
     *                role)
     * @return {@link AdminResponse} 등록된 Admin 정보
     */
    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminResponse> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        return ApiResponse
                .success(adminService.createAdmin(request.getLoginId(), request.getBoardId(), request.getRole()));
    }

    /**
     * 모든 Admin 조회
     * 
     * @return {@link AdminResponse} 모든 Admin 목록
     */
    @GetMapping("/admins")
    public ApiResponse<List<AdminResponse>> getAllAdmins() {
        return ApiResponse.success(adminService.getAllAdmins());
    }

    /**
     * Admin 비활성화
     * 
     * @param adminId 비활성화할 Admin ID
     * @return 성공 응답
     */
    @PutMapping("/admins/{adminId}/deactivate")
    public ApiResponse<Void> deactivateAdmin(@PathVariable Long adminId) {
        adminService.deactivateAdmin(adminId);
        return ApiResponse.success(null);
    }

    /**
     * Admin 활성화
     * 
     * @param adminId 활성화할 Admin ID
     * @return 성공 응답
     */
    @PutMapping("/admins/{adminId}/activate")
    public ApiResponse<Void> activateAdmin(@PathVariable Long adminId) {
        adminService.activateAdmin(adminId);
        return ApiResponse.success(null);
    }

    /**
     * IP 차단
     * 
     * @param request        {@link IpBlockRequest} 차단할 IP 정보 (ipAddress, reason,
     *                       endDate)
     * @param authentication 인증 정보
     * @return {@link IpBlockResponse} 차단된 IP 정보
     */
    @PostMapping("/ip-blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IpBlockResponse> blockIp(
            @Valid @RequestBody IpBlockRequest request,
            Authentication authentication) {
        Long adminUserId = ((CustomUserDetails) authentication.getPrincipal()).getUserId();
        return ApiResponse.success(adminService.blockIp(adminUserId, request.getIpAddress(), request.getReason(),
                request.getEndDate()));
    }

    /**
     * IP 차단 해제
     * 
     * @param ipAddress 차단 해제할 IP 주소
     * @return 성공 응답
     */
    @DeleteMapping("/ip-blocks/{ipAddress}")
    public ApiResponse<Void> unblockIp(@PathVariable String ipAddress) {
        adminService.unblockIp(ipAddress);
        return ApiResponse.success(null);
    }

    /**
     * 차단된 IP 목록 조회
     * 
     * @return {@link IpBlockResponse} 차단된 IP 목록
     */
    @GetMapping("/ip-blocks")
    public ApiResponse<List<IpBlockResponse>> getBlockedIps() {
        return ApiResponse.success(adminService.getBlockedIps());
    }

    /**
     * 대시보드 통계 조회
     * 
     * @return {@link DashboardStatsDto} 대시보드 통계 정보
     */
    @GetMapping("/stats")
    public ApiResponse<DashboardStatsDto> getDashboardStats() {
        return ApiResponse.success(adminService.getDashboardStats());
    }
}
