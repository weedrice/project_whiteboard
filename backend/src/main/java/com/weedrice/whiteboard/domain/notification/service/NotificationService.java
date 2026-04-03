package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final UserNotificationSettingsRepository userNotificationSettingsRepository;

    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    @TransactionalEventListener
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void handleNotificationEvent(NotificationEvent event) {
        if (isSelfNotification(event) || !isNotificationEnabled(event)) {
            return;
        }

        Notification notification = persistNotification(event);

        if (notification != null) {
            deliverNotification(event.getUserToNotify().getUserId(), notification);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        String connectionId = UUID.randomUUID().toString();

        emitters.computeIfAbsent(userId, ignored -> new ConcurrentHashMap<>())
                .put(connectionId, emitter);

        emitter.onCompletion(() -> removeEmitter(userId, connectionId));
        emitter.onTimeout(() -> removeEmitter(userId, connectionId));
        emitter.onError((e) -> removeEmitter(userId, connectionId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (IOException e) {
            removeEmitter(userId, connectionId);
        }

        return emitter;
    }

    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }

        for (Long userId : new ArrayList<>(emitters.keySet())) {
            Map<String, SseEmitter> userEmitters = emitters.get(userId);
            if (userEmitters == null || userEmitters.isEmpty()) {
                emitters.remove(userId);
                continue;
            }

            for (Map.Entry<String, SseEmitter> entry : new ArrayList<>(userEmitters.entrySet())) {
                try {
                    entry.getValue().send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    removeEmitter(userId, entry.getKey());
                }
            }
        }
    }

    private Notification persistNotification(NotificationEvent event) {
        return notificationRepository.save(Notification.builder()
                .user(event.getUserToNotify())
                .actor(event.getActor())
                .actorAgent(event.getActorAgent())
                .notificationType(event.getNotificationType())
                .sourceType(event.getSourceType())
                .sourceId(event.getSourceId())
                .content(event.getContent())
                .build());
    }

    private void deliverNotification(Long userId, Notification notification) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null || userEmitters.isEmpty()) {
            return;
        }

        NotificationResponse.NotificationSummary summary = NotificationResponse.NotificationSummary.from(notification);
        for (Map.Entry<String, SseEmitter> entry : new ArrayList<>(userEmitters.entrySet())) {
            try {
                entry.getValue().send(SseEmitter.event()
                        .name("notification")
                        .data(summary));
            } catch (IOException e) {
                removeEmitter(userId, entry.getKey());
            }
        }
    }

    private boolean isSelfNotification(NotificationEvent event) {
        return event.getActor() != null
                && event.getUserToNotify() != null
                && event.getUserToNotify().getUserId().equals(event.getActor().getUserId());
    }

    private boolean isNotificationEnabled(NotificationEvent event) {
        if (event.getUserToNotify() == null || event.getNotificationType() == null) {
            return true;
        }

        return userNotificationSettingsRepository
                .findByUserIdAndNotificationType(event.getUserToNotify().getUserId(), event.getNotificationType())
                .map(setting -> Boolean.TRUE.equals(setting.getIsEnabled()))
                .orElse(true);
    }

    private void removeEmitter(Long userId, String connectionId) {
        Map<String, SseEmitter> userEmitters = emitters.get(userId);
        if (userEmitters == null) {
            return;
        }

        userEmitters.remove(connectionId);
        if (userEmitters.isEmpty()) {
            emitters.remove(userId);
        }
    }

    public NotificationResponse getNotifications(Long userId, Pageable pageable) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Page<Notification> notificationPage = notificationRepository.findByUserOrderByCreatedAtDesc(user, pageable);
        return NotificationResponse.from(notificationPage);
    }

    @Transactional
    public void readNotification(Long userId, Long notificationId) {
        Notification notification = notificationRepository.findById(notificationId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        if (!notification.getUser().getUserId().equals(userId)) {
            throw new BusinessException(ErrorCode.FORBIDDEN);
        }
        notification.read();
    }

    @Transactional
    public void readAllNotifications(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        notificationRepository.readAllByUser(user);
    }

    public long getUnreadNotificationCount(Long userId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return notificationRepository.countByUserAndIsRead(user, false);
    }
}
