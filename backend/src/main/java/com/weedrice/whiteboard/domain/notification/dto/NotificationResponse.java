package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.List;
import java.util.stream.Collectors;

@Getter
@Builder
public class NotificationResponse {
    private List<NotificationSummary> content;
    private int page;
    private int size;
    private long totalElements;
    private int totalPages;
    private boolean hasNext;
    private boolean hasPrevious;

    @Getter
    @Builder
    public static class NotificationSummary {
        private Long notificationId;
        private String notificationType;
        private String message;
        private UserInfo actor;
        private String sourceType;
        private Long sourceId;
        private boolean isRead;
        private LocalDateTime createdAt;

        public static NotificationSummary from(Notification notification) {
            return NotificationSummary.builder()
                    .notificationId(notification.getNotificationId())
                    .notificationType(notification.getNotificationType())
                    .message(notification.getContent())
                    .actor(UserInfo.builder()
                            .userId(notification.getActor().getUserId())
                            .displayName(notification.getActor().getDisplayName())
                            .build())
                    .sourceType(notification.getSourceType())
                    .sourceId(notification.getSourceId())
                    .isRead(notification.getIsRead())
                    .createdAt(notification.getCreatedAt())
                    .build();
        }
    }

    @Getter
    @Builder
    public static class UserInfo {
        private Long userId;
        private String displayName;
    }

    public static NotificationResponse from(Page<Notification> notificationPage) {
        List<NotificationSummary> content = notificationPage.getContent().stream()
                .map(notification -> NotificationSummary.builder()
                        .notificationId(notification.getNotificationId())
                        .notificationType(notification.getNotificationType())
                        .message(notification.getContent())
                        .actor(UserInfo.builder()
                                .userId(notification.getActor().getUserId())
                                .displayName(notification.getActor().getDisplayName())
                                .build())
                        .sourceType(notification.getSourceType())
                        .sourceId(notification.getSourceId())
                        .isRead(notification.getIsRead())
                        .createdAt(notification.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        return NotificationResponse.builder()
                .content(content)
                .page(notificationPage.getNumber())
                .size(notificationPage.getSize())
                .totalElements(notificationPage.getTotalElements())
                .totalPages(notificationPage.getTotalPages())
                .hasNext(notificationPage.hasNext())
                .hasPrevious(notificationPage.hasPrevious())
                .build();
    }
}
