package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.AllArgsConstructor;
import lombok.Getter;

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

    public NotificationEvent(User userToNotify, User actor, NotificationType notificationType,
            NotificationSourceType sourceType, Long sourceId,
            String content) {
        this(userToNotify, actor, null, notificationType, sourceType, sourceId, content);
    }
}
