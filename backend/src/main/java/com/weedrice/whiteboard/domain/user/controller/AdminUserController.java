package com.weedrice.whiteboard.domain.user.controller;

import com.weedrice.whiteboard.domain.user.dto.AdminUserCommentResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserDetailResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserPostResponse;
import com.weedrice.whiteboard.domain.user.dto.AdminUserSubscriptionResponse;
import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.dto.UserStatusUpdateRequest;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.service.UserAdminCommandService;
import com.weedrice.whiteboard.domain.user.service.UserAdminQueryService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
@PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
public class AdminUserController {

    private final UserAdminQueryService userAdminQueryService;
    private final UserAdminCommandService userAdminCommandService;

    @GetMapping
    public ApiResponse<PageResponse<UserAdminResponse>> searchUsers(
            @RequestParam(required = false) String keyword,
            @RequestParam(required = false, name = "q") String q,
            @RequestParam(required = false) String status,
            @RequestParam(required = false) String role,
            @RequestParam(required = false) Boolean isEmailVerified,
            @RequestParam(required = false) Boolean isSuperAdmin,
            @RequestParam(required = false) Boolean isWithdrawn,
            @RequestParam(required = false) LocalDate createdFrom,
            @RequestParam(required = false) LocalDate createdTo,
            @RequestParam(required = false) LocalDate lastLoginFrom,
            @RequestParam(required = false) LocalDate lastLoginTo,
            @RequestParam(required = false) Long minActivityCount,
            @PageableDefault(size = 20) Pageable pageable) {
        String searchKeyword = (keyword != null && !keyword.isBlank()) ? keyword : q;
        Page<UserAdminResponse> response = userAdminQueryService.searchUsersForAdmin(
                searchKeyword,
                status,
                role,
                isEmailVerified,
                isSuperAdmin,
                isWithdrawn,
                createdFrom,
                createdTo,
                lastLoginFrom,
                lastLoginTo,
                minActivityCount,
                pageable);
        return ApiResponse.success(new PageResponse<>(response));
    }

    @GetMapping("/{userId}")
    public ApiResponse<AdminUserDetailResponse> getUserDetail(@PathVariable Long userId) {
        return ApiResponse.success(userAdminQueryService.getUserDetailForAdmin(userId));
    }

    @GetMapping("/{userId}/posts")
    public ApiResponse<PageResponse<AdminUserPostResponse>> getUserPosts(
            @PathVariable Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success(new PageResponse<>(userAdminQueryService.getUserPostsForAdmin(userId, pageable)));
    }

    @GetMapping("/{userId}/comments")
    public ApiResponse<PageResponse<AdminUserCommentResponse>> getUserComments(
            @PathVariable Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success(new PageResponse<>(userAdminQueryService.getUserCommentsForAdmin(userId, pageable)));
    }

    @GetMapping("/{userId}/subscriptions")
    public ApiResponse<PageResponse<AdminUserSubscriptionResponse>> getUserSubscriptions(
            @PathVariable Long userId,
            @PageableDefault(size = 10) Pageable pageable) {
        return ApiResponse.success(new PageResponse<>(userAdminQueryService.getUserSubscriptionsForAdmin(userId, pageable)));
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody UserStatusUpdateRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userAdminCommandService.updateUserStatus(requiredUserId(userDetails), userId, request.getStatus());
        return ApiResponse.success();
    }
}

