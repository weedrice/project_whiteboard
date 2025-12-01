package com.weedrice.whiteboard.domain.user.controller;

import com.weedrice.whiteboard.domain.board.dto.BoardResponse;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.dto.*;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserService;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
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
    private final BoardService boardService;
    private final PostService postService;
    private final CommentService commentService;

    @GetMapping("/me")
    public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(@AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.getMyInfo(userDetails.getUserId());
        MyInfoResponse response = new MyInfoResponse(user.getUserId(), user.getLoginId(), user.getEmail(), user.getDisplayName(), user.getProfileImageUrl(), user.getStatus(), "Y".equals(user.getIsEmailVerified()), user.getCreatedAt());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/{userId}")
    public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(@PathVariable Long userId) {
        UserService.UserProfileDto profile = userService.getUserProfile(userId);
        UserProfileResponse response = new UserProfileResponse(profile.getUserId(), profile.getLoginId(), profile.getDisplayName(), profile.getProfileImageUrl(), profile.getCreatedAt(), profile.getPostCount(), profile.getCommentCount());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me")
    public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateMyProfile(
            @Valid @RequestBody UpdateProfileRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        User user = userService.updateMyProfile(userDetails.getUserId(), request.getDisplayName(), request.getProfileImageUrl());
        UpdateProfileResponse response = new UpdateProfileResponse(user.getUserId(), user.getDisplayName(), user.getProfileImageUrl());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/password")
    public ResponseEntity<ApiResponse<MessageResponse>> updatePassword(
            @Valid @RequestBody UpdatePasswordRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.updatePassword(userDetails.getUserId(), request.getCurrentPassword(), request.getNewPassword());
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse("비밀번호가 변경되었습니다.")));
    }

    @DeleteMapping("/me")
    public ResponseEntity<ApiResponse<MessageResponse>> deleteAccount(
            @Valid @RequestBody DeleteAccountRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userService.deleteAccount(userDetails.getUserId(), request.getPassword());
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse("회원 탈퇴가 완료되었습니다.")));
    }

    @GetMapping("/me/settings")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> getMySettings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        UserSettings settings = userSettingsService.getSettings(userDetails.getUserId());
        UserSettingsResponse response = new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(), "Y".equals(settings.getHideNsfw()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/settings")
    public ResponseEntity<ApiResponse<UserSettingsResponse>> updateMySettings(
            @Valid @RequestBody UpdateSettingsRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserSettings settings = userSettingsService.updateSettings(userDetails.getUserId(), request.getTheme(), request.getLanguage(), request.getTimezone(), request.getHideNsfw());
        UserSettingsResponse response = new UserSettingsResponse(settings.getTheme(), settings.getLanguage(), settings.getTimezone(), "Y".equals(settings.getHideNsfw()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @GetMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getMyNotificationSettings(@AuthenticationPrincipal CustomUserDetails userDetails) {
        List<UserNotificationSettings> settings = userSettingsService.getNotificationSettings(userDetails.getUserId());
        List<NotificationSettingResponse> response = settings.stream()
                .map(s -> new NotificationSettingResponse(s.getNotificationType(), "Y".equals(s.getIsEnabled())))
                .collect(Collectors.toList());
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PutMapping("/me/notification-settings")
    public ResponseEntity<ApiResponse<NotificationSettingResponse>> updateMyNotificationSetting(
            @Valid @RequestBody UpdateNotificationSettingRequest request,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        UserNotificationSettings setting = userSettingsService.updateNotificationSetting(userDetails.getUserId(), request.getNotificationType(), request.getIsEnabled());
        NotificationSettingResponse response = new NotificationSettingResponse(setting.getNotificationType(), "Y".equals(setting.getIsEnabled()));
        return ResponseEntity.ok(ApiResponse.success(response));
    }

    @PostMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<MessageResponse>> blockUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userBlockService.blockUser(userDetails.getUserId(), userId);
        return ResponseEntity.status(HttpStatus.CREATED).body(ApiResponse.success(new MessageResponse("차단되었습니다.")));
    }

    @DeleteMapping("/{userId}/block")
    public ResponseEntity<ApiResponse<MessageResponse>> unblockUser(
            @PathVariable Long userId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        userBlockService.unblockUser(userDetails.getUserId(), userId);
        return ResponseEntity.ok(ApiResponse.success(new MessageResponse("차단이 해제되었습니다.")));
    }

    @GetMapping("/me/blocks")
    public ResponseEntity<ApiResponse<PageResponse<BlockedUserResponse>>> getBlockedUsers(
            Pageable pageable,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Page<UserBlockService.BlockedUserDto> blockedUsers = userBlockService.getBlockedUsers(userDetails.getUserId(), pageable);
        Page<BlockedUserResponse> response = blockedUsers.map(dto -> new BlockedUserResponse(dto.getUserId(), dto.getLoginId(), dto.getDisplayName(), dto.getBlockedAt()));
        return ResponseEntity.ok(ApiResponse.success(new PageResponse<>(response)));
    }

    @GetMapping("/me/subscriptions")
    public ApiResponse<PageResponse<BoardResponse>> getMySubscriptions(@AuthenticationPrincipal CustomUserDetails userDetails, Pageable pageable) {
        Page<BoardResponse> response = boardService.getMySubscriptions(userDetails.getUserId(), pageable);
        return ApiResponse.success(new PageResponse<>(response));
    }

    @GetMapping("/me/posts")
    public ApiResponse<PageResponse<PostSummary>> getMyPosts(@AuthenticationPrincipal CustomUserDetails userDetails, Pageable pageable) {
        Page<com.weedrice.whiteboard.domain.post.entity.Post> posts = postService.getMyPosts(userDetails.getUserId(), pageable);
        
        List<PostSummary> summaries = new java.util.ArrayList<>();
        long totalElements = posts.getTotalElements();
        int pageNumber = posts.getNumber();
        int pageSize = posts.getSize();
        
        for (int i = 0; i < posts.getContent().size(); i++) {
            com.weedrice.whiteboard.domain.post.entity.Post post = posts.getContent().get(i);
            PostSummary summary = PostSummary.from(post);
            summary.setRowNum(totalElements - ((long) pageNumber * pageSize) - i);
            summaries.add(summary);
        }
        
        Page<PostSummary> summaryPage = new org.springframework.data.domain.PageImpl<>(summaries, pageable, totalElements);
        return ApiResponse.success(new PageResponse<>(summaryPage));
    }

    @GetMapping("/me/comments")
    public ApiResponse<PageResponse<MyCommentResponse>> getMyComments(@AuthenticationPrincipal CustomUserDetails userDetails, Pageable pageable) {
        Page<MyCommentResponse> response = commentService.getMyComments(userDetails.getUserId(), pageable).map(MyCommentResponse::from);
        return ApiResponse.success(new PageResponse<>(response));
    }

    @GetMapping("/me/history/views")
    public ApiResponse<PageResponse<PostSummary>> getRecentlyViewedPosts(
            @AuthenticationPrincipal CustomUserDetails userDetails,
            Pageable pageable) {
        Page<PostSummary> response = postService.getRecentlyViewedPosts(userDetails.getUserId(), pageable);
        return ApiResponse.success(new PageResponse<>(response));
    }
}
