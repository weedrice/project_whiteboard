package com.weedrice.whiteboard.domain.user.controller;

import com.weedrice.whiteboard.domain.user.dto.*;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserService;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

    private final UserService userService;
    private final UserSettingsService userSettingsService;
    private final UserBlockService userBlockService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(Authentication authentication) {
        Long userId = extractUserId(authentication);
        User user = userService.getMyInfo(userId);

        MyInfoResponse response = MyInfoResponse.builder()
                .userId(user.getUserId())
                .loginId(user.getLoginId())
                .email(user.getEmail())
                .displayName(user.getDisplayName())
                .profileImageUrl(user.getProfileImageUrl())
                .status(user.getStatus())
                .isEmailVerified("Y".equals(user.getIsEmailVerified()))
                .createdAt(user.getCreatedAt())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(@PathVariable Long userId) {
        UserService.UserProfileDto profile = userService.getUserProfile(userId);

        UserProfileResponse response = UserProfileResponse.builder()
                .userId(profile.getUserId())
                .loginId(profile.getLoginId())
                .displayName(profile.getDisplayName())
                .profileImageUrl(profile.getProfileImageUrl())
                .createdAt(profile.getCreatedAt())
                .postCount(profile.getPostCount())
                .commentCount(profile.getCommentCount())
                .build();

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);

        User user = userService.updateMyProfile(userId, request.getDisplayName(), request.getProfileImageUrl());

        UpdateProfileResponse response = new UpdateProfileResponse(
                user.getUserId(),
                user.getDisplayName(),
                user.getProfileImageUrl()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<MessageResponse>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);

        userService.deleteAccount(userId, request.getPassword());

        return ResponseEntity.ok(ApiResponse.success(new MessageResponse("회원 탈퇴가 완료되었습니다.")));
    }

    @GetMapping("/me/settings")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getMySettings(Authentication authentication) {
        Long userId = extractUserId(authentication);
        UserSettings settings = userSettingsService.getSettings(userId);

        UserSettingsResponse response = new UserSettingsResponse(
                settings.getTheme(),
                settings.getLanguage(),
                settings.getTimezone(),
                "Y".equals(settings.getHideNsfw())
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/settings")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateMySettings(
            @Valid @RequestBody UpdateSettingsRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);

        UserSettings settings = userSettingsService.updateSettings(
                userId,
                request.getTheme(),
                request.getLanguage(),
                request.getTimezone(),
                request.getHideNsfw()
        );

        UserSettingsResponse response = new UserSettingsResponse(
                settings.getTheme(),
                settings.getLanguage(),
                settings.getTimezone(),
                "Y".equals(settings.getHideNsfw())
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getMyNotificationSettings(Authentication authentication) {
        Long userId = extractUserId(authentication);
        List<UserNotificationSettings> settings = userSettingsService.getNotificationSettings(userId);

        List<NotificationSettingResponse> response = settings.stream()
                .map(s -> new NotificationSettingResponse(
                        s.getId().getNotificationType(),
                        "Y".equals(s.getIsEnabled())
                ))
                .collect(Collectors.toList());

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateMyNotificationSetting(
            @Valid @RequestBody UpdateNotificationSettingRequest request,
            Authentication authentication) {
        Long userId = extractUserId(authentication);

        UserNotificationSettings setting = userSettingsService.updateNotificationSetting(
                userId,
                request.getNotificationType(),
                request.getIsEnabled()
        );

        NotificationSettingResponse response = new NotificationSettingResponse(
                setting.getId().getNotificationType(),
                "Y".equals(setting.getIsEnabled())
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<MessageResponse>> blockUser(
            @PathVariable Long userId,
            Authentication authentication) {
        Long currentUserId = extractUserId(authentication);

        userBlockService.blockUser(currentUserId, userId);

        return ResponseEntity.status(HttpStatus.CREATED)
                .body(ApiResponse.success(new MessageResponse("차단되었습니다.")));
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<MessageResponse>> unblockUser(
            @PathVariable Long userId,
            Authentication authentication) {
        Long currentUserId = extractUserId(authentication);

        userBlockService.unblockUser(currentUserId, userId);

        return ResponseEntity.ok(ApiResponse.success(new MessageResponse("차단이 해제되었습니다.")));
    }

    @GetMapping("/me/blocks")
    public ResponseEntity<ApiResponse<BlockedUsersResponse>> getBlockedUsers(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            Authentication authentication) {
        Long userId = extractUserId(authentication);

        Pageable pageable = PageRequest.of(page, size);
        Page<UserBlockService.BlockedUserDto> blockedUsers = userBlockService.getBlockedUsers(userId, pageable);

        List<BlockedUserResponse> content = blockedUsers.getContent().stream()
                .map(dto -> new BlockedUserResponse(
                        dto.getUserId(),
                        dto.getLoginId(),
                        dto.getDisplayName(),
                        dto.getBlockedAt()
                ))
                .collect(Collectors.toList());

        BlockedUsersResponse response = new BlockedUsersResponse(
                content,
                blockedUsers.getNumber(),
                blockedUsers.getSize(),
                blockedUsers.getTotalElements(),
                blockedUsers.getTotalPages(),
                blockedUsers.hasNext(),
                blockedUsers.hasPrevious()
        );

        return ResponseEntity.ok(ApiResponse.success(response));
    }

    private Long extractUserId(Authentication authentication) {
        String loginId = authentication.getName();
        return userService.findUserIdByLoginId(loginId);
    }
}