package com.weedrice.whiteboard.domain.notification.controller;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.service.NotificationService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import com.weedrice.whiteboard.global.security.CustomUserDetails;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.web.bind.annotation.*;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static com.weedrice.whiteboard.global.security.AuthenticatedUserResolver.requiredUserId;

@RestController
@RequestMapping("/api/v1/notifications")
@RequiredArgsConstructor
public class NotificationController {

    private final NotificationService notificationService;

    @GetMapping
    public ApiResponse<NotificationResponse> getNotifications(
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        Pageable pageable = PageRequestUtils.of(page, size);
        return ApiResponse.success(notificationService.getNotifications(userId, pageable));
    }

    @PutMapping("/{notificationId}/read")
    public ApiResponse<Void> readNotification(
            @PathVariable Long notificationId,
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        notificationService.readNotification(userId, notificationId);
        return ApiResponse.success(null);
    }

    @PutMapping("/read-all")
    public ApiResponse<Void> readAllNotifications(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        notificationService.readAllNotifications(userId);
        return ApiResponse.success(null);
    }

    @GetMapping("/unread-count")
    public ApiResponse<Long> getUnreadNotificationCount(
            @AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return ApiResponse.success(notificationService.getUnreadNotificationCount(userId));
    }

    @GetMapping(value = "/stream", produces = org.springframework.http.MediaType.TEXT_EVENT_STREAM_VALUE)
    public SseEmitter subscribe(@AuthenticationPrincipal CustomUserDetails userDetails) {
        Long userId = requiredUserId(userDetails);
        return notificationService.subscribe(userId);
    }
}
