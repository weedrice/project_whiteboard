package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private final java.util.Map<Long, org.springframework.web.servlet.mvc.method.annotation.SseEmitter> emitters = new java.util.concurrent.ConcurrentHashMap<>();

    // @TransactionalEventListener 메서드에 @Transactional을 붙일 경우 REQUIRES_NEW 또는
    // NOT_SUPPORTED를 명시해야 함
    @Transactional(propagation = Propagation.NOT_SUPPORTED) // 트랜잭션 없이 실행
    @TransactionalEventListener
    public void handleNotificationEvent(NotificationEvent event) {
        // 자기 자신에게는 알림을 보내지 않음
        if (event.getUserToNotify().getUserId().equals(event.getActor().getUserId())) {
            return;
        }

        Notification notification = transactionTemplate.execute(status -> {
            Notification noti = Notification.builder()
                    .user(event.getUserToNotify())
                    .actor(event.getActor())
                    .notificationType(event.getNotificationType())
                    .sourceType(event.getSourceType())
                    .sourceId(event.getSourceId())
                    .content(event.getContent())
                    .build();
            return notificationRepository.save(noti);
        });

        // SSE 전송
        if (notification != null) {
            sendNotificationToUser(event.getUserToNotify().getUserId(), notification);
        }
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public org.springframework.web.servlet.mvc.method.annotation.SseEmitter subscribe(Long userId) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = new org.springframework.web.servlet.mvc.method.annotation.SseEmitter(
                Long.MAX_VALUE);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        // 503 Service Unavailable 방지를 위한 더미 데이터 전송
        try {
            emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (java.io.IOException e) {
            emitters.remove(userId);
        }

        return emitter;
    }

    private void sendNotificationToUser(Long userId, Notification notification) {
        org.springframework.web.servlet.mvc.method.annotation.SseEmitter emitter = emitters.get(userId);
        if (emitter != null) {
            try {
                emitter.send(org.springframework.web.servlet.mvc.method.annotation.SseEmitter.event()
                        .name("notification")
                        .data(com.weedrice.whiteboard.domain.notification.dto.NotificationResponse.NotificationSummary
                                .from(notification)));
            } catch (java.io.IOException e) {
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
