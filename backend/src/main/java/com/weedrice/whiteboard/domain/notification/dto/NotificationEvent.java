package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;
import java.util.UUID;

@Getter
public class NotificationEvent {
    private final UUID eventId;
    private User userToNotify;
    private User actor;
    private Agent actorAgent;
    private NotificationType notificationType;
    private NotificationSourceType sourceType;
    private Long sourceId;
    private String content;
    private String messageKey;
    private List<String> messageParams;
    private boolean pushEnabled;

    public NotificationEvent(User userToNotify, User actor, NotificationType notificationType,
            NotificationSourceType sourceType, Long sourceId,
            String content) {
        this(UUID.randomUUID(), userToNotify, actor, null, notificationType, sourceType, sourceId,
                content, null, List.of());
    }

    public NotificationEvent(User userToNotify, User actor, Agent actorAgent, NotificationType notificationType,
            NotificationSourceType sourceType, Long sourceId, String content) {
        this(UUID.randomUUID(), userToNotify, actor, actorAgent, notificationType, sourceType, sourceId,
                content, null, List.of());
    }

    private NotificationEvent(
            UUID eventId,
            User userToNotify,
            User actor,
            Agent actorAgent,
            NotificationType notificationType,
            NotificationSourceType sourceType,
            Long sourceId,
            String content,
            String messageKey,
            List<String> messageParams) {
        this.eventId = eventId;
        this.userToNotify = userToNotify;
        this.actor = actor;
        this.actorAgent = actorAgent;
        this.notificationType = notificationType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.content = content;
        this.messageKey = messageKey;
        this.messageParams = messageParams;
        this.pushEnabled = true;
    }

    public static NotificationEvent localized(
            User userToNotify,
            User actor,
            NotificationType notificationType,
            NotificationSourceType sourceType,
            Long sourceId,
            String messageKey,
            String... messageParams) {
        return localized(userToNotify, actor, null, notificationType, sourceType, sourceId, messageKey, messageParams);
    }

    public static NotificationEvent inAppOnlyLocalized(
            User userToNotify,
            User actor,
            NotificationType notificationType,
            NotificationSourceType sourceType,
            Long sourceId,
            String messageKey,
            String... messageParams) {
        NotificationEvent event = localized(
                userToNotify, actor, notificationType, sourceType, sourceId, messageKey, messageParams);
        event.pushEnabled = false;
        return event;
    }

    public static NotificationEvent localized(
            User userToNotify,
            User actor,
            Agent actorAgent,
            NotificationType notificationType,
            NotificationSourceType sourceType,
            Long sourceId,
            String messageKey,
            String... messageParams) {
        return new NotificationEvent(
                UUID.randomUUID(),
                userToNotify,
                actor,
                actorAgent,
                notificationType,
                sourceType,
                sourceId,
                null,
                messageKey,
                messageParams == null
                        ? List.of()
                        : Arrays.stream(messageParams)
                                .map(param -> param == null ? "" : param)
                                .toList());
    }

    public static NotificationEvent restored(
            UUID eventId,
            User userToNotify,
            User actor,
            Agent actorAgent,
            NotificationType notificationType,
            NotificationSourceType sourceType,
            Long sourceId,
            String content,
            String messageKey,
            List<String> messageParams) {
        return new NotificationEvent(
                eventId,
                userToNotify,
                actor,
                actorAgent,
                notificationType,
                sourceType,
                sourceId,
                content,
                messageKey,
                messageParams == null ? List.of() : List.copyOf(messageParams));
    }
}
