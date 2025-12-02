package com.weedrice.whiteboard.domain.user.controller;

import com.weedrice.whiteboard.domain.user.dto.UserAdminResponse;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.web.PageableDefault;
import org.springframework.security.access.prepost.PreAuthorize;
import com.weedrice.whiteboard.domain.user.dto.UserStatusUpdateRequest;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/admin/users")
@RequiredArgsConstructor
public class AdminUserController {

    private final UserService userService;

    @GetMapping
    public ApiResponse<Page<UserAdminResponse>> searchUsers(
            @RequestParam(required = false) String keyword,
            @PageableDefault(size = 20) Pageable pageable) {
        Page<User> users = userService.searchUsers(keyword, pageable);
        Page<UserAdminResponse> response = users.map(UserAdminResponse::from);
        return ApiResponse.success(response);
    }

    @PutMapping("/{userId}/status")
    public ApiResponse<Void> updateUserStatus(
            @PathVariable Long userId,
            @RequestBody UserStatusUpdateRequest request) {
        userService.updateUserStatus(userId, request.getStatus());
        return ApiResponse.success(null);
    }
}
