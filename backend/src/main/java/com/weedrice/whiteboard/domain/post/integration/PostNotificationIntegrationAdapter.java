package com.weedrice.whiteboard.domain.post.integration;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.post.port.PostNotificationPort;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class PostNotificationIntegrationAdapter implements PostNotificationPort {

    private final UserRepository userRepository;
    private final ApplicationEventPublisher eventPublisher;

    @Override
    public void publishSystemNotification(
            Long recipientUserId,
            NotificationSourceType sourceType,
            Long sourceId,
            String messageKey,
            String messageArgument) {
        User recipient = userRepository.findById(recipientUserId)
                .orElseThrow(() -> new IllegalStateException(
                        "Post notification recipient not found: " + recipientUserId));
        eventPublisher.publishEvent(NotificationEvent.localized(
                recipient,
                null,
                NotificationType.SYSTEM,
                sourceType,
                sourceId,
                messageKey,
                messageArgument));
    }
}
