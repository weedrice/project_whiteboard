package com.weedrice.whiteboard.domain.admin.controller;

import com.weedrice.whiteboard.domain.admin.dto.*;

import com.weedrice.whiteboard.domain.admin.service.AdminAssignmentFacade;
import com.weedrice.whiteboard.domain.admin.service.AdminDashboardService;
import com.weedrice.whiteboard.domain.admin.service.AdminReadService;
import com.weedrice.whiteboard.domain.admin.service.IpBlockService;
import com.weedrice.whiteboard.domain.admin.service.SuperAdminService;
import com.weedrice.whiteboard.domain.post.dto.PostResponse;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.entity.Role;

import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.web.PageableDefault;
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

    private final AdminAssignmentFacade adminAssignmentFacade;
    private final AdminReadService adminReadService;
    private final AdminDashboardService adminDashboardService;
    private final SuperAdminService superAdminService;
    private final IpBlockService ipBlockService;
    private final PostService postService;

    /**
     * Super Admin 목록을 조회한다.
     *
     * @return {@link SuperAdminResponse} Super Admin 목록
     */
    @GetMapping("/super")
    public ApiResponse<List<SuperAdminResponse>> getSuperAdmin() {
        return ApiResponse.success(superAdminService.getSuperAdmin());
    }

    /**
     * Super Admin을 등록한다.
     * <p>
     * {@link BusinessException} 등록 사용자 없을 시 <code>USER_NOT_FOUND</code><br>
     * 중복 등록 시 <code>DUPLICATE_RESOURCE</code> 반환
     *
     * @param request {@link SuperAdminRequest} 등록 대상 사용자의 loginId를 포함하는 객체
     * @return {@link SuperAdminUpdateResponse} 등록된 Super Admin 정보
     */
    @PutMapping("/super/active")
    public ApiResponse<SuperAdminUpdateResponse> activeSuperAdmin(@Valid @RequestBody SuperAdminRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(superAdminService.createSuperAdmin(request.getLoginId(), userId, request.getReason()));
    }

    /**
     * Super Admin을 해제한다.
     * <p>
     * {@link BusinessException} 등록 사용자 없을 시 <code>USER_NOT_FOUND</code><br>
     * 미등록 사용자 해제 시 <code>INVALID_TARGET</code> 반환
     *
     * @param request {@link SuperAdminRequest} 해제 대상 사용자의 loginId를 포함하는 객체
     * @return {@link SuperAdminUpdateResponse} 해제된 Super Admin 정보
     */
    @PutMapping("/super/deactive")
    public ApiResponse<SuperAdminUpdateResponse> deactivateSuperAdmin(
            @Valid @RequestBody SuperAdminRequest request,
            @CurrentUserId Long userId) {
        return ApiResponse.success(superAdminService.deactivateSuperAdmin(
                request.getLoginId(),
                userId,
                request.getReason()));
    }

    /**
     * Admin을 등록한다.
     *
     * @param request {@link AdminCreateRequest} 등록할 Admin 정보 (loginId, boardId, role)
     * @return {@link AdminResponse} 등록된 Admin 정보
     */
    @PostMapping("/admins")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<AdminResponse> createAdmin(@Valid @RequestBody AdminCreateRequest request) {
        return ApiResponse
                .success(adminAssignmentFacade.createAdmin(request.getLoginId(), request.getBoardId(), request.getRole()));
    }

    /**
     * 모든 Admin 목록을 조회한다.
     *
     * @return {@link AdminResponse} 모든 Admin 목록
     */
    @GetMapping("/admins")
    public ApiResponse<PageResponse<AdminResponse>> getAllAdmins(
            @PageableDefault(size = 20, sort = "adminId", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponses.page(adminReadService.getAllAdmins(pageable));
    }

    /**
     * Admin을 비활성화한다.
     *
     * @param adminId 비활성화할 Admin ID
     * @return 성공 응답
     */
    @PutMapping("/admins/{adminId}/deactivate")
    public ApiResponse<Void> deactivateAdmin(@PathVariable Long adminId) {
        adminAssignmentFacade.deactivateAdmin(adminId);
        return ApiResponses.ok();
    }

    /**
     * Admin 활성화
     * 
     * @param adminId 활성화할 Admin ID
     * @return 성공 응답
     */
    @PutMapping("/admins/{adminId}/activate")
    public ApiResponse<Void> activateAdmin(@PathVariable Long adminId) {
        adminAssignmentFacade.activateAdmin(adminId);
        return ApiResponses.ok();
    }

    @GetMapping("/boards/{boardId}/manager")
    public ApiResponse<AdminResponse> getBoardManager(@PathVariable Long boardId) {
        return ApiResponse.success(adminAssignmentFacade.getBoardManager(boardId));
    }

    @PutMapping("/boards/{boardId}/manager")
    public ApiResponse<AdminResponse> replaceBoardManager(@PathVariable Long boardId,
            @Valid @RequestBody BoardManagerUpdateRequest request) {
        return ApiResponse.success(adminAssignmentFacade.replaceBoardManager(boardId, request.getLoginId()));
    }

    /**
     * IP를 차단한다.
     *
     * @param request {@link IpBlockRequest} 차단할 IP 정보 (ipAddress, reason, endDate)
     * @return {@link IpBlockResponse} 차단된 IP 정보
     */
    @PostMapping("/ip-blocks")
    @ResponseStatus(HttpStatus.CREATED)
    public ApiResponse<IpBlockResponse> blockIp(
            @Valid @RequestBody IpBlockRequest request,
            @CurrentUserId Long adminUserId) {
        return ApiResponse.success(ipBlockService.blockIp(adminUserId, request.getIpAddress(), request.getReason(),
                request.getEndDate()));
    }

    /**
     * IP 차단을 해제한다.
     *
     * @param ipAddress 차단 해제할 IP 주소
     * @return 성공 응답
     */
    @DeleteMapping("/ip-blocks/{ipAddress}")
    public ApiResponse<Void> unblockIp(@PathVariable String ipAddress) {
        ipBlockService.unblockIp(ipAddress);
        return ApiResponses.ok();
    }

    /**
     * 차단된 IP 목록을 조회한다.
     *
     * @return {@link IpBlockResponse} 차단된 IP 목록
     */
    @GetMapping("/ip-blocks")
    public ApiResponse<PageResponse<IpBlockResponse>> getBlockedIps(
            @PageableDefault(size = 20, sort = "startDate", direction = Sort.Direction.DESC) Pageable pageable) {
        return ApiResponses.page(ipBlockService.getBlockedIps(pageable));
    }

    /**
     * 대시보드 통계를 조회한다.
     *
     * @return {@link DashboardStatsDto} 대시보드 통계 정보
     */
    @GetMapping("/stats")
    public ApiResponse<DashboardStatsDto> getDashboardStats() {
        return ApiResponse.success(adminDashboardService.getDashboardStats());
    }

    @GetMapping("/stats/deep")
    public ApiResponse<DeepDashboardStatsDto> getDeepDashboardStats(
            @RequestParam(defaultValue = "30") int days) {
        return ApiResponse.success(adminDashboardService.getDeepDashboardStats(days));
    }

    @GetMapping("/inquiries")
    public ApiResponse<PageResponse<AdminInquirySummaryResponse>> getInquiryPosts(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Sort sort) {
        Pageable pageable = PageRequestUtils.of(page, size);
        Page<AdminInquirySummaryResponse> inquiryPage = postService.getInquiryPostsForAdmin(pageable);
        return ApiResponses.page(inquiryPage);
    }

    @GetMapping("/inquiries/{postId}")
    public ApiResponse<PostResponse> getInquiryPost(@PathVariable Long postId) {
        return ApiResponse.success(postService.getInquiryPostResponseForAdmin(postId));
    }
}
