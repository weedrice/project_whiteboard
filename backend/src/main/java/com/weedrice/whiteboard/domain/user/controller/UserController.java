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
import com.weedrice.whiteboard.global.security.CurrentUserId;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.util.List;
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
                        @CurrentUserId(required = false) Long viewerUserId) {
                return ResponseEntity.ok(ApiResponse.success(userProfileService.getUserProfile(userId, viewerUserId)));
        }

        @GetMapping("/me")
        public ResponseEntity<ApiResponse<MyInfoResponse>> getMyInfo(
                        @CurrentUserId Long userId) {
                return ResponseEntity.ok(ApiResponse.success(userProfileService.getMyInfo(userId)));
        }

        @PutMapping("/me")
        public ResponseEntity<ApiResponse<UpdateProfileResponse>> updateMyProfile(
                        @Valid @RequestBody UpdateProfileRequest request,
                        @CurrentUserId Long userId) {
                return ResponseEntity.ok(ApiResponse.success(userProfileService.updateMyProfile(
                                userId,
                                request.getDisplayName(),
                                request.getProfileImageId())));
        }

        @PostMapping("/me/email-verification")
        public ResponseEntity<ApiResponse<Void>> verifyEmail(
                        @Valid @RequestBody EmailVerificationConfirmRequest request,
                        @CurrentUserId Long userId) {
                userSecurityService.verifyAndChangeEmail(
                                userId,
                                request.getEmail(),
                                request.getVerificationTicket());
                return ResponseEntity.ok(ApiResponse.success());
        }

        @PutMapping("/me/password")
        public ResponseEntity<ApiResponse<MessageResponse>> updatePassword(
                        @Valid @RequestBody UpdatePasswordRequest request,
                        @CurrentUserId Long userId) {
                userSecurityService.updatePassword(userId, request.getCurrentPassword(),
                                request.getNewPassword());
                return userActionResponseFactory.passwordChanged();
        }

        @DeleteMapping("/me")
        public ResponseEntity<ApiResponse<MessageResponse>> deleteAccount(
                        @Valid @RequestBody DeleteAccountRequest request,
                        @CurrentUserId Long userId) {
                userProfileService.deleteAccount(userId, request.getPassword());
                return userActionResponseFactory.accountDeleted();
        }

        @GetMapping("/me/settings")
        public ResponseEntity<ApiResponse<UserSettingsResponse>> getMySettings(
                        @CurrentUserId Long userId) {
                return ResponseEntity.ok(ApiResponse.success(userSettingsService.getSettings(userId)));
        }

        @PutMapping("/me/settings")
        public ResponseEntity<ApiResponse<UserSettingsResponse>> updateMySettings(
                        @Valid @RequestBody UpdateSettingsRequest request,
                        @CurrentUserId Long userId) {
                return ResponseEntity.ok(ApiResponse.success(userSettingsService.updateSettings(userId,
                                request.getTheme(), request.getLanguage(), request.getTimezone(),
                                request.getHideNsfw())));
        }

        @GetMapping("/me/notification-settings")
        public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> getMyNotificationSettings(
                        @CurrentUserId Long userId) {
                return ResponseEntity.ok(ApiResponse
                                .success(userSettingsService.getNotificationSettings(userId)));
        }

        @PutMapping("/me/notification-settings/bulk")
        public ResponseEntity<ApiResponse<List<NotificationSettingResponse>>> updateMyNotificationSettings(
                        @Valid @RequestBody UpdateNotificationSettingsRequest request,
                        @CurrentUserId Long userId) {
                return ResponseEntity.ok(ApiResponse.success(userSettingsService.updateNotificationSettings(
                                userId,
                                request.getSettings())));
        }

        @PostMapping("/{userId}/block")
        public ResponseEntity<ApiResponse<MessageResponse>> blockUser(
                        @PathVariable Long userId,
                        @CurrentUserId Long currentUserId) {
                userBlockService.blockUser(currentUserId, userId);
                return userActionResponseFactory.blocked();
        }

        @DeleteMapping("/{userId}/block")
        public ResponseEntity<ApiResponse<MessageResponse>> unblockUser(
                        @PathVariable Long userId,
                        @CurrentUserId Long currentUserId) {
                userBlockService.unblockUser(currentUserId, userId);
                return userActionResponseFactory.unblocked();
        }

        @GetMapping("/me/blocks")
        public ResponseEntity<ApiResponse<PageResponse<BlockedUserResponse>>> getBlockedUsers(
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort,
                        @CurrentUserId Long userId) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<BlockedUserResponse> response = userBlockService.getBlockedUsers(userId, pageable);
                return ResponseEntity.ok(pageResponse(response));
        }

        @GetMapping("/me/subscriptions")
        public ApiResponse<PageResponse<SubscriptionBoardResponse>> getMySubscriptions(
                        @CurrentUserId Long userId,
                        @RequestParam(defaultValue = "false") boolean includeUnavailable,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                        Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<SubscriptionBoardResponse> response = boardService.getMySubscriptions(
                                userId,
                                pageable,
                                includeUnavailable);
                return pageResponse(response);
        }

        @PostMapping("/me/agents/claim")
        public ApiResponse<AgentResponse> claimAgent(
                        @Valid @RequestBody AgentClaimRequest request,
                        @CurrentUserId Long userId,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                return ApiResponse.success(
                                agentLifecycleService.claim(userId, request,
                                                agentRequestContextResolver.resolve(httpServletRequest)));
        }

        @GetMapping("/me/agents")
        public ApiResponse<AgentListResponse> getMyAgents(
                        @CurrentUserId Long userId) {
                return ApiResponse.success(agentLifecycleService.getMyAgents(userId));
        }

        @PatchMapping("/me/agents/{agentId}/suspend")
        public ApiResponse<AgentResponse> suspendMyAgent(
                        @PathVariable Long agentId,
                        @CurrentUserId Long userId,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                return ApiResponse.success(
                                agentLifecycleService.suspendMyAgent(userId, agentId,
                                                agentRequestContextResolver.resolve(httpServletRequest)));
        }

        @PatchMapping("/me/agents/{agentId}/activate")
        public ApiResponse<AgentResponse> activateMyAgent(
                        @PathVariable Long agentId,
                        @CurrentUserId Long userId,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                return ApiResponse.success(
                                agentLifecycleService.activateMyAgent(userId, agentId,
                                                agentRequestContextResolver.resolve(httpServletRequest)));
        }

        @DeleteMapping("/me/agents/{agentId}")
        public ApiResponse<Void> deleteMyAgent(
                        @PathVariable Long agentId,
                        @CurrentUserId Long userId,
                        jakarta.servlet.http.HttpServletRequest httpServletRequest) {
                agentLifecycleService.deleteMyAgent(userId, agentId,
                                agentRequestContextResolver.resolve(httpServletRequest));
                return ApiResponse.success();
        }

        @GetMapping("/me/posts")
        public ApiResponse<PageResponse<PostSummary>> getMyPosts(@CurrentUserId Long userId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<PostSummary> response = postService.getMyPosts(userId, pageable);
                return pageResponse(response);
        }

        @GetMapping("/me/comments")
        public ApiResponse<PageResponse<MyCommentResponse>> getMyComments(
                        @CurrentUserId Long userId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<MyCommentResponse> response = commentService.getMyComments(userId, pageable);
                return pageResponse(response);
        }

        @GetMapping("/me/history/views")
        public ApiResponse<PageResponse<PostSummary>> getRecentlyViewedPosts(
                        @CurrentUserId Long userId,
                        @RequestParam(defaultValue = "0") int page,
                        @RequestParam(defaultValue = "20") int size,
                Sort sort) {
                Pageable pageable = PageRequestUtils.of(page, size, sort);
                Page<PostSummary> response = postService.getRecentlyViewedPosts(userId, pageable);
                return pageResponse(response);
        }

        private <T> ApiResponse<PageResponse<T>> pageResponse(Page<T> page) {
                return ApiResponse.success(new PageResponse<>(page));
        }
}
