package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
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
import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private final Map<Long, SseEmitter> emitters = new ConcurrentHashMap<>();

    @TransactionalEventListener
    public void handleNotificationEvent(NotificationEvent event) {
        if (event.getUserToNotify().getUserId().equals(event.getActor().getUserId())) {
            return;
        }

        Notification notification = transactionTemplate.execute(status -> {
            Notification noti = Notification.builder()
                    .user(event.getUserToNotify())
                    .actor(event.getActor())
                    .actorAgent(event.getActorAgent())
                    .notificationType(event.getNotificationType())
                    .sourceType(event.getSourceType())
                    .sourceId(event.getSourceId())
                    .content(event.getContent())
                    .build();
            return notificationRepository.save(noti);
        });

        if (notification != null) {
            sendNotificationToUser(event.getUserToNotify().getUserId(), notification);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (IOException e) {
            emitters.remove(userId);
        }

        return emitter;
    }

    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeat() {
        if (emitters.isEmpty()) {
            return;
        }
        for (Long userId : new ArrayList<>(emitters.keySet())) {
            SseEmitter emitter = emitters.get(userId);
            if (emitter != null) {
                try {
                    emitter.send(SseEmitter.event().comment("heartbeat"));
                } catch (IOException e) {
                    emitters.remove(userId);
                }
            }
        }
    }

    private void sendNotificationToUser(Long userId, Notification notification) {
        SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(SseEmitter.event()
                        .name("notification")
                        .data(NotificationResponse.NotificationSummary.from(notification)));
            } catch (IOException e) {
                emitters.remove(userId);
            }
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
