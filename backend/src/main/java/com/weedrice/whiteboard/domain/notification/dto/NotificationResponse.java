package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.actor.AuthorPresentation;
import lombok.Builder;
import lombok.Getter;
import org.springframework.data.domain.Page;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Set;
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
        private static final Set<String> ACTOR_NAME_MESSAGE_KEYS = Set.of(
                "notification.comment.created",
                "notification.reply.created",
                "notification.comment.liked",
                "notification.post.liked",
                "notification.message.received",
                "notification.mention.created");
        private Long notificationId;
        private String notificationType;
        private String message;
        private String messageKey;
        private List<String> messageParams;
        private ActorInfo actor;
        private String sourceType;
        private Long sourceId;
        private Boolean isRead;
        private LocalDateTime createdAt;
        private Integer groupCount;
        private Boolean grouped;
        private LocalDateTime lastEventAt;
        private String targetUrl;

        public static NotificationSummary from(Notification notification) {
            return from(notification, null);
        }

        public static NotificationSummary from(Notification notification, String targetUrl) {
            return from(notification, targetUrl, false);
        }

        public static NotificationSummary from(Notification notification, String targetUrl, boolean hideActor) {
            List<String> messageParams = NotificationMessageParamsCodec.decode(notification.getMessageParams());
            String message = notification.getContent();
            boolean withdrawn = !hideActor && notification.getActor() != null
                    && AuthorPresentation.isWithdrawn(notification.getActor().getStatus(), notification.getActor().getDeletedAt());
            if (hideActor || withdrawn) {
                String replacement = withdrawn ? AuthorPresentation.WITHDRAWN_DISPLAY_NAME : "";
                String currentActorName = resolveActorName(notification);
                String storedActorName = resolveStoredActorName(notification, messageParams, currentActorName);
                // Replace both names in one pass: one may be a substring of the other.
                boolean actorMessage = notification.getMessageKey() == null
                        || ACTOR_NAME_MESSAGE_KEYS.contains(notification.getMessageKey());
                if (hideActor || actorMessage) {
                    boolean hasStoredActorName = notification.getMessageKey() != null
                            && ACTOR_NAME_MESSAGE_KEYS.contains(notification.getMessageKey())
                            && !messageParams.isEmpty() && !messageParams.getFirst().isBlank();
                    if (withdrawn && (!hasStoredActorName
                            || !containsActorName(message, currentActorName, storedActorName))) {
                        // Without a structured historic name, substring replacement can expose part of an old name.
                        message = AuthorPresentation.WITHDRAWN_DISPLAY_NAME + "의 알림입니다.";
                    } else {
                        message = replaceActorNames(message, currentActorName, storedActorName, replacement);
                    }
                }
                if (notification.getMessageKey() != null
                        && ACTOR_NAME_MESSAGE_KEYS.contains(notification.getMessageKey())
                        && !messageParams.isEmpty()) {
                    java.util.ArrayList<String> maskedParams = new java.util.ArrayList<>(messageParams);
                    maskedParams.set(0, replacement);
                    messageParams = List.copyOf(maskedParams);
                }
            }
            return NotificationSummary.builder()
                    .notificationId(notification.getNotificationId())
                    .notificationType(notification.getNotificationType().name())
                    .message(message)
                    .messageKey(notification.getMessageKey())
                    .messageParams(messageParams)
                    .actor(hideActor ? ActorInfo.hidden() : ActorInfo.from(notification))
                    .sourceType(notification.getSourceType())
                    .sourceId(notification.getSourceId())
                    .isRead(notification.getIsRead())
                    .createdAt(notification.getCreatedAt())
                    .groupCount(notification.getGroupCount())
                    .grouped(notification.isGrouped())
                    .lastEventAt(notification.getLastEventAt())
                    .targetUrl(targetUrl)
                    .build();
        }

        private static String resolveActorName(Notification notification) {
            if (notification.getActorAgent() != null) {
                return notification.getActorAgent().getName();
            }
            return notification.getActor() != null ? notification.getActor().getDisplayName() : null;
        }

        private static String resolveStoredActorName(
                Notification notification,
                List<String> messageParams,
                String fallback) {
            if (notification.getMessageKey() != null
                    && ACTOR_NAME_MESSAGE_KEYS.contains(notification.getMessageKey())
                    && !messageParams.isEmpty()) {
                return messageParams.getFirst();
            }
            return fallback;
        }

        private static boolean containsActorName(String message, String... names) {
            return message != null && java.util.Arrays.stream(names)
                    .anyMatch(name -> name != null && !name.isBlank() && message.contains(name));
        }

        private static String replaceActorNames(String message, String currentName, String storedName, String replacement) {
            if (message == null) {
                return null;
            }
            String pattern = java.util.stream.Stream.of(currentName, storedName)
                    .filter(name -> name != null && !name.isBlank())
                    .distinct()
                    .sorted(java.util.Comparator.comparingInt(String::length).reversed())
                    .map(java.util.regex.Pattern::quote)
                    .collect(Collectors.joining("|"));
            return pattern.isEmpty() ? message : message.replaceAll(pattern, java.util.regex.Matcher.quoteReplacement(replacement));
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
        private boolean withdrawn;

        public static ActorInfo from(Notification notification) {
            if (notification.getActor() != null
                    && AuthorPresentation.isWithdrawn(notification.getActor().getStatus(), notification.getActor().getDeletedAt())) {
                return ActorInfo.builder()
                        .userId(notification.getActor().getUserId())
                        .agentId(notification.getActorAgent() == null ? null : notification.getActorAgent().getAgentId())
                        .authorType(notification.getActorAgent() == null ? "USER" : "AGENT")
                        .displayName(AuthorPresentation.WITHDRAWN_DISPLAY_NAME)
                        .withdrawn(true)
                        .build();
            }
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

        private static ActorInfo hidden() {
            return ActorInfo.builder()
                    .authorType("USER")
                    .displayName("")
                    .build();
        }
    }

    public static NotificationResponse from(Page<Notification> notificationPage) {
        return from(notificationPage, Collections.emptyMap());
    }

    public static NotificationResponse from(Page<Notification> notificationPage, Map<Long, String> targetUrls) {
        return from(notificationPage, targetUrls, Set.of());
    }

    public static NotificationResponse from(
            Page<Notification> notificationPage,
            Map<Long, String> targetUrls,
            Set<Long> hiddenActorUserIds) {
        List<NotificationSummary> content = notificationPage.getContent().stream()
                .map(notification -> NotificationSummary.from(
                        notification,
                        notification.getNotificationId() != null
                                ? targetUrls.get(notification.getNotificationId())
                                : null,
                        notification.getActor() != null
                                && hiddenActorUserIds.contains(notification.getActor().getUserId())))
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
