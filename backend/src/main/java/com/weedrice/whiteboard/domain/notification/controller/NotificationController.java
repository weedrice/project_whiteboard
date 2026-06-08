package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.service.NotificationService;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.ApiResponses;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;
    private final NotificationSseEmitterRegistry notificationSseEmitterRegistry;

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
    public SseEmitter subscribe(@CurrentUserId Long userId) {
        notificationService.validateStreamSubscription(userId);
        return notificationSseEmitterRegistry.subscribe(userId);
    }
}
