package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

import java.util.Arrays;
import java.util.List;

@Getter
@AllArgsConstructor
public class NotificationEvent {
    private User userToNotify;
    private User actor;
    private Agent actorAgent;
    private NotificationType notificationType;
    private NotificationSourceType sourceType;
    private Long sourceId;
    private String content;
    private String messageKey;
    private List<String> messageParams;

    public NotificationEvent(User userToNotify, User actor, NotificationType notificationType,
            NotificationSourceType sourceType, Long sourceId,
            String content) {
        this(userToNotify, actor, null, notificationType, sourceType, sourceId, content, null, List.of());
    }

    public NotificationEvent(User userToNotify, User actor, Agent actorAgent, NotificationType notificationType,
            NotificationSourceType sourceType, Long sourceId, String content) {
        this(userToNotify, actor, actorAgent, notificationType, sourceType, sourceId, content, null, List.of());
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
}
