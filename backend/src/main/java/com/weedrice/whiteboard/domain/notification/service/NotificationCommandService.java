package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationMessageParamsCodec;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.context.MessageSource;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Locale;
import java.util.Set;

@Service
@RequiredArgsConstructor
class NotificationCommandService {
    private static final int MAX_CONTENT_LENGTH = 255;
    private static final Set<String> GROUPABLE_TYPES = Set.of("COMMENT", "REPLY", "LIKE", "KEYWORD");

    private final NotificationRepository notificationRepository;
    private final NotificationPreferenceService preferenceService;
    private final UserRepository userRepository;
    private final PushDeliveryJobEnqueuer pushDeliveryJobEnqueuer;
    private final UserSettingsRepository userSettingsRepository;
    private final MessageSource messageSource;

    Notification handleNotificationEvent(NotificationEvent event) {
        if (!hasRequiredPayload(event)) {
            return null;
        }
        User receiver = resolveActiveReceiver(event);
        if (receiver == null) {
            return null;
        }
        if (preferenceService.isSelfNotification(event) || !preferenceService.isNotificationEnabled(event)) {
            return null;
        }
        String content = normalizeContent(resolveContent(event, receiver.getUserId()));
        if (content == null) {
            return null;
        }
        String messageParams = NotificationMessageParamsCodec.encode(event.getMessageParams());

        LocalDateTime eventAt = LocalDateTime.now();
        String groupKey = createGroupKey(receiver.getUserId(), event);
        if (isGroupable(event) && groupKey != null) {
            Notification existing = notificationRepository
                    .findFirstByUser_UserIdAndGroupKeyAndIsReadOrderByLastEventAtDescNotificationIdDesc(
                            receiver.getUserId(),
                            groupKey,
                            false)
                    .orElse(null);
            if (existing != null) {
                existing.merge(event.getActor(), event.getActorAgent(), content, event.getMessageKey(), messageParams,
                        eventAt);
                pushDeliveryJobEnqueuer.enqueue(event, existing);
                return existing;
            }
        }

        Notification notification = notificationRepository.save(Notification.builder()
                .user(receiver)
                .actor(event.getActor())
                .actorAgent(event.getActorAgent())
                .notificationType(event.getNotificationType())
                .sourceType(event.getSourceType().getValue())
                .sourceId(event.getSourceId())
                .content(content)
                .messageKey(event.getMessageKey())
                .messageParams(messageParams)
                .groupKey(groupKey)
                .lastEventAt(eventAt)
                .build());
        pushDeliveryJobEnqueuer.enqueue(event, notification);
        return notification;
    }

    private boolean isGroupable(NotificationEvent event) {
        return event != null
                && event.getNotificationType() != null
                && GROUPABLE_TYPES.contains(event.getNotificationType().name());
    }

    private String createGroupKey(Long receiverUserId, NotificationEvent event) {
        if (receiverUserId == null || event == null || event.getNotificationType() == null
                || event.getSourceType() == null || event.getSourceId() == null) {
            return null;
        }
        return receiverUserId + ":" + event.getNotificationType().name()
                + ":" + event.getSourceType().getValue()
                + ":" + event.getSourceId();
    }

    private boolean hasRequiredPayload(NotificationEvent event) {
        return event != null
                && event.getUserToNotify() != null
                && event.getUserToNotify().getUserId() != null
                && event.getNotificationType() != null
                && event.getSourceType() != null
                && event.getSourceId() != null
                && (hasText(event.getContent()) || hasText(event.getMessageKey()));
    }

    private String resolveContent(NotificationEvent event, Long receiverUserId) {
        if (!hasText(event.getMessageKey())) {
            return event.getContent();
        }
        List<String> params = event.getMessageParams() == null ? List.of() : event.getMessageParams();
        return messageSource.getMessage(
                event.getMessageKey(),
                params.toArray(),
                hasText(event.getContent()) ? event.getContent() : event.getMessageKey(),
                resolveLocale(receiverUserId));
    }

    private Locale resolveLocale(Long userId) {
        if (userId == null) {
            return Locale.KOREAN;
        }
        String language = userSettingsRepository.findSettingsReadByUserId(userId)
                .map(UserSettingsRepository.SettingsReadProjection::getLanguage)
                .orElse("ko");
        return "en".equalsIgnoreCase(language) ? Locale.ENGLISH : Locale.KOREAN;
    }

    private boolean hasText(String value) {
        return value != null && !value.isBlank();
    }

    private User resolveActiveReceiver(NotificationEvent event) {
        Long receiverId = event.getUserToNotify().getUserId();
        User locked = userRepository.findByIdForUpdate(receiverId).orElse(null);
        if (locked == null) {
            return userRepository.findByUserIdAndStatusAndDeletedAtIsNull(receiverId, User.STATUS_ACTIVE)
                    .orElse(null);
        }
        return java.util.Optional.of(locked)
                .filter(user -> User.STATUS_ACTIVE.equals(user.getStatus()))
                .filter(user -> user.getDeletedAt() == null)
                .orElse(null);
    }

    private String normalizeContent(String content) {
        if (content == null) {
            return null;
        }
        String normalizedContent = content.strip();
        if (normalizedContent.isBlank()) {
            return null;
        }
        if (normalizedContent.codePointCount(0, normalizedContent.length()) > MAX_CONTENT_LENGTH) {
            int endIndex = normalizedContent.offsetByCodePoints(0, MAX_CONTENT_LENGTH);
            return normalizedContent.substring(0, endIndex);
        }
        return normalizedContent;
    }

    void readNotification(Long userId, Long notificationId) {
        int updatedRows = notificationRepository.markReadByNotificationIdAndUserId(notificationId, userId);
        if (updatedRows > 0) {
            return;
        }
        throw new BusinessException(ErrorCode.NOT_FOUND);
    }

    void readAllNotifications(Long userId) {
        notificationRepository.readAllByUserId(userId);
    }
}
