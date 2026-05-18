package com.weedrice.whiteboard.domain.user.controller;

import com.weedrice.whiteboard.domain.agent.dto.AgentClaimRequest;
import com.weedrice.whiteboard.domain.agent.dto.AgentListResponse;
import com.weedrice.whiteboard.domain.agent.dto.AgentResponse;
import com.weedrice.whiteboard.domain.agent.service.AgentLifecycleService;
import com.weedrice.whiteboard.domain.agent.web.AgentRequestContextResolver;
import com.weedrice.whiteboard.domain.auth.dto.EmailVerificationConfirmRequest;
import com.weedrice.whiteboard.domain.board.dto.SubscriptionBoardResponse;
import com.weedrice.whiteboard.domain.board.service.BoardService;
import com.weedrice.whiteboard.domain.comment.dto.MyCommentResponse;
import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.dto.PostSummary;
import com.weedrice.whiteboard.domain.post.service.PostService;
import com.weedrice.whiteboard.domain.user.dto.*;
import com.weedrice.whiteboard.domain.user.service.UserBlockService;
import com.weedrice.whiteboard.domain.user.service.UserProfileService;
import com.weedrice.whiteboard.domain.user.service.UserSecurityService;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.web.UserActionResponseFactory;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.dto.PageResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.Objects;
import java.util.stream.Collectors;

@RestController
@RequestMapping("/api/v1/users")
@RequiredArgsConstructor
public class UserController {

        private final UserProfileService userProfileService;
        private final UserSecurityService userSecurityService;
        private final UserSettingsService userSettingsService;
        private final UserBlockService userBlockService;
        private final BoardService boardService;
        private final PostService postService;
        private final CommentService commentService;
        private final AgentLifecycleService agentLifecycleService;
        private final UserActionResponseFactory userActionResponseFactory;
        private final AgentRequestContextResolver agentRequestContextResolver;

        @GetMapping("/{userId}")
        public ResponseEntity<ApiResponse<UserProfileResponse>> getUserProfile(
                        @PathVariable Long userId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                Long viewerUserId = userDetails != null ? userDetails.getUserId() : null;
                return ResponseEntity.ok(ApiResponse.success(userProfileService.getUserProfile(userId, viewerUserId)));
        }

        @GetMapping("/me")
        public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ResponseEntity.ok(ApiResponse.success(userProfileService.getMyInfo(userDetails.getUserId())));
        }

        @PutMapping("/me")
        public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateMyProfile(
                        @Valid @RequestBody UpdateProfileRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ResponseEntity.ok(ApiResponse.success(userProfileService.updateMyProfile(userDetails.getUserId(),
                                request.getDisplayName(), request.getProfileImageId())));
        }

        @PostMapping("/me/email-verification")
        public ResponseEntity<ApiResponse<Void>> verifyEmail(
                        @Valid @RequestBody EmailVerificationConfirmRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                userSecurityService.verifyAndChangeEmail(
                                userDetails.getUserId(),
                                request.getEmail(),
                                request.getVerificationTicket());
                return ResponseEntity.ok(ApiResponse.success(null));
        }

        @PutMapping("/me/password")
        public ResponseEntity<ApiResponse<MessageResponse>> updatePassword(
                        @Valid @RequestBody UpdatePasswordRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                userSecurityService.updatePassword(userDetails.getUserId(), request.getCurrentPassword(),
                                request.getNewPassword());
                return userActionResponseFactory.passwordChanged();
        }

        @DeleteMapping("/me")
        public ResponseEntity<ApiResponse<MessageResponse>> deleteAccount(
                        @Valid @RequestBody DeleteAccountRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                userProfileService.deleteAccount(userDetails.getUserId(), request.getPassword());
                return userActionResponseFactory.accountDeleted();
        }

        @GetMapping("/me/settings")
        public ResponseEntity<ApiResponse<UserSettingsResponse>> getMySettings(
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ResponseEntity.ok(ApiResponse.success(userSettingsService.getSettings(userDetails.getUserId())));
        }

        @PutMapping("/me/settings")
        public ResponseEntity<ApiResponse<UserSettingsResponse>> updateMySettings(
                        @Valid @RequestBody UpdateSettingsRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ResponseEntity.ok(ApiResponse.success(userSettingsService.updateSettings(userDetails.getUserId(),
                                request.getTheme(), request.getLanguage(), request.getTimezone(),
                                request.getHideNsfw())));
        }

        @GetMapping("/me/notification-settings")
        public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getMyNotificationSettings(
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ResponseEntity.ok(ApiResponse
                                .success(userSettingsService.getNotificationSettings(userDetails.getUserId())));
        }

        @PutMapping("/me/notification-settings/bulk")
        public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> updateMyNotificationSettings(
                        @Valid @RequestBody UpdateNotificationSettingsRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ResponseEntity.ok(ApiResponse.success(userSettingsService.updateNotificationSettings(
                                userDetails.getUserId(),
                                request.getSettings())));
        }

        @PostMapping("/{userId}/block")
        public ResponseEntity<ApiResponse<MessageResponse>> blockUser(
                        @PathVariable Long userId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                userBlockService.blockUser(userDetails.getUserId(), userId);
                return userActionResponseFactory.blocked();
        }

        @DeleteMapping("/{userId}/block")
        public ResponseEntity<ApiResponse<MessageResponse>> unblockUser(
                        @PathVariable Long userId,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                userBlockService.unblockUser(userDetails.getUserId(), userId);
                return userActionResponseFactory.unblocked();
        }

        @GetMapping("/me/blocks")
        public ResponseEntity<ApiResponse<PageResponse<BlockedUserResponse>>> getBlockedUsers(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort,
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<BlockedUserResponse> response = userBlockService.getBlockedUsers(userDetails.getUserId(),
                                pageable);
                return ResponseEntity.ok(ApiResponse.success(new PageResponse<>(response)));
        }

        @GetMapping("/me/subscriptions")
        public ApiResponse<PageResponse<SubscriptionBoardResponse>> getMySubscriptions(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "false") boolean includeUnavailable,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<SubscriptionBoardResponse> response = boardService.getMySubscriptions(
                                userDetails.getUserId(),
                                pageable,
                                includeUnavailable);
                return ApiResponse.success(new PageResponse<>(response));
        }

        @PostMapping("/me/agents/claim")
        public ApiResponse<AgentResponse> claimAgent(
                        @Valid @RequestBody AgentClaimRequest request,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                return ApiResponse.success(
                                agentLifecycleService.claim(userDetails.getUserId(), request,
                                                agentRequestContextResolver.resolve(httpServletRequest)));
        }

        @GetMapping("/me/agents")
        public ApiResponse<AgentListResponse> getMyAgents(
                        @AuthenticationPrincipal CustomUserDetails userDetails) {
                return ApiResponse.success(agentLifecycleService.getMyAgents(userDetails.getUserId()));
        }

        @PatchMapping("/me/agents/{agentId}/suspend")
        public ApiResponse<AgentResponse> suspendMyAgent(
                        @PathVariable Long agentId,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                return ApiResponse.success(
                                agentLifecycleService.suspendMyAgent(userDetails.getUserId(), agentId,
                                                agentRequestContextResolver.resolve(httpServletRequest)));
        }

        @PatchMapping("/me/agents/{agentId}/activate")
        public ApiResponse<AgentResponse> activateMyAgent(
                        @PathVariable Long agentId,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                return ApiResponse.success(
                                agentLifecycleService.activateMyAgent(userDetails.getUserId(), agentId,
                                                agentRequestContextResolver.resolve(httpServletRequest)));
        }

        @DeleteMapping("/me/agents/{agentId}")
        public ApiResponse<Void> deleteMyAgent(
                        @PathVariable Long agentId,
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                agentLifecycleService.deleteMyAgent(userDetails.getUserId(), agentId,
                                agentRequestContextResolver.resolve(httpServletRequest));
                return ApiResponse.success(null);
        }

        @GetMapping("/me/posts")
        public ApiResponse<PageResponse<PostSummary>> getMyPosts(@AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<PostSummary> response = postService.getMyPosts(userDetails.getUserId(), pageable);
                return ApiResponse.success(new PageResponse<>(response));
        }

        @GetMapping("/me/comments")
        public ApiResponse<PageResponse<MyCommentResponse>> getMyComments(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<MyCommentResponse> response = commentService.getMyComments(userDetails.getUserId(), pageable);
                return ApiResponse.success(new PageResponse<>(response));
        }

        @GetMapping("/me/history/views")
        public ApiResponse<PageResponse<PostSummary>> getRecentlyViewedPosts(
                        @AuthenticationPrincipal CustomUserDetails userDetails,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Long userId = Objects.requireNonNull(userDetails.getUserId(), "UserId must not be null");
                Page<PostSummary> response = postService.getRecentlyViewedPosts(userId, pageable);
                return ApiResponse.success(new PageResponse<>(response));
        }
}
