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
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.event.TransactionalEventListener;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class NotificationService {

    private final NotificationRepository notificationRepository;
    private final UserRepository userRepository;
    private final org.springframework.transaction.support.TransactionTemplate transactionTemplate;

    private final java.util.Map<Long, SseEmitter> emitters = new java.util.concurrent.ConcurrentHashMap<>();

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
    public SseEmitter subscribe(Long userId) {
        SseEmitter emitter = new SseEmitter(Long.MAX_VALUE);
        emitters.put(userId, emitter);

        emitter.onCompletion(() -> emitters.remove(userId));
        emitter.onTimeout(() -> emitters.remove(userId));
        emitter.onError((e) -> emitters.remove(userId));

        // 503 Service Unavailable 방지를 위한 더미 데이터 전송
        try {
            emitter.send(SseEmitter.event()
                    .name("connect")
                    .data("connected!"));
        } catch (IOException e) {
            emitters.remove(userId);
        }

        return emitter;
    }

    /**
     * 프록시/로드밸런서 유휴 타임아웃으로 연결이 끊기는 것을 방지하기 위해
     * 주기적으로 SSE comment(heartbeat)를 전송합니다.
     * ERR_INCOMPLETE_CHUNKED_ENCODING 방지.
     */
    @Scheduled(fixedRate = 25_000) // 25초마다
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
                        .data(com.weedrice.whiteboard.domain.notification.dto.NotificationResponse.NotificationSummary
                                .from(notification)));
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
