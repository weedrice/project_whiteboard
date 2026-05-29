package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
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
        private ActorInfo actor;
        private String sourceType;
        private Long sourceId;
        private Boolean isRead;
        private LocalDateTime createdAt;
        private String targetUrl;

        public static NotificationSummary from(Notification notification) {
            return from(notification, null);
        }

        public static NotificationSummary from(Notification notification, String targetUrl) {
            return NotificationSummary.builder()
                    .notificationId(notification.getNotificationId())
                    .notificationType(notification.getNotificationType().name())
                    .message(notification.getContent())
                    .actor(ActorInfo.from(notification))
                    .sourceType(notification.getSourceType())
                    .sourceId(notification.getSourceId())
                    .isRead(notification.getIsRead())
                    .createdAt(notification.getCreatedAt())
                    .targetUrl(targetUrl)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class ActorInfo {
        private Long userId;
        private Long agentId;
        private String authorType;
        private String displayName;
        private String profileImageUrl;

        public static ActorInfo from(Notification notification) {
            if (notification.getActorAgent() != null) {
                return ActorInfo.builder()
                        .userId(notification.getActor() != null ? notification.getActor().getUserId() : null)
                        .agentId(notification.getActorAgent().getAgentId())
                        .authorType("AGENT")
                        .displayName(notification.getActorAgent().getName())
                        .profileImageUrl(null)
                        .build();
            }

            if (notification.getActor() == null) {
                return ActorInfo.builder()
                        .authorType("SYSTEM")
                        .displayName("")
                        .build();
            }

            return ActorInfo.builder()
                    .userId(notification.getActor().getUserId())
                    .agentId(null)
                    .authorType("USER")
                    .displayName(notification.getActor().getDisplayName())
                    .profileImageUrl(notification.getActor().getProfileImageUrl())
                    .build();
        }
    }

    public static NotificationResponse from(Page<Notification> notificationPage) {
        return from(notificationPage, Collections.emptyMap());
    }

    public static NotificationResponse from(Page<Notification> notificationPage, Map<Long, String> targetUrls) {
        List<NotificationSummary> content = notificationPage.getContent().stream()
                .map(notification -> NotificationSummary.from(
                        notification,
                        notification.getNotificationId() != null
                                ? targetUrls.get(notification.getNotificationId())
                                : null))
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
