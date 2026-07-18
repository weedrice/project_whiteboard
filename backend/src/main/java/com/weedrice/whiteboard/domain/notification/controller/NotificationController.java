package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.dto.CommentTopicSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.service.NotificationService;
import com.weedrice.whiteboard.domain.notification.service.CommentTopicAccessService;
import com.weedrice.whiteboard.domain.notification.service.NotificationStreamSubscriptionService;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import com.weedrice.whiteboard.global.security.SessionAuthenticationToken;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.Authentication;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final CommentTopicAccessService commentTopicAccessService;
    private final NotificationSseEmitterRegistry notificationSseEmitterRegistry;
    private final NotificationStreamSubscriptionService notificationStreamSubscriptionService;

    @GetMapping
    public ApiResponse<NotificationResponse> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @CurrentUserId Long userId) {
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponse.success(notificationService.getNotifications(userId, pageable));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> readNotification(
            @PathVariable Long notificationId,
            @CurrentUserId Long userId) {
        notificationService.readNotification(userId, notificationId);
        return ApiResponses.ok();
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> readAllNotifications(
            @CurrentUserId Long userId) {
        notificationService.readAllNotifications(userId);
        return ApiResponses.ok();
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadNotificationCount(
            @CurrentUserId Long userId) {
        return ApiResponse.success(notificationService.getUnreadNotificationCount(userId));
    }

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@CurrentUserId Long userId, Authentication authentication) {
        if (!(authentication instanceof SessionAuthenticationToken sessionAuthentication)) {
            throw new BusinessException(ErrorCode.UNAUTHORIZED);
        }
        return notificationStreamSubscriptionService.subscribe(
                userId,
                sessionAuthentication.getSessionFamilyId());
    }

    @PostMapping("/comment-topics/{postId}/subscriptions")
    public ApiResponse<Void> subscribeCommentTopic(
            @PathVariable Long postId,
            @Valid @RequestBody CommentTopicSubscriptionRequest request,
            @CurrentUserId Long userId) {
        notificationService.validateStreamSubscription(userId);
        commentTopicAccessService.subscribeReadable(userId, postId, request.getSubscriberId());
        return ApiResponses.ok();
    }

    @DeleteMapping("/comment-topics/{postId}/subscriptions/{subscriberId}")
    public ApiResponse<Void> unsubscribeCommentTopic(
            @PathVariable Long postId,
            @PathVariable String subscriberId,
            @CurrentUserId Long userId) {
        notificationSseEmitterRegistry.unsubscribeCommentTopic(userId, postId, subscriberId);
        return ApiResponses.ok();
    }
}
