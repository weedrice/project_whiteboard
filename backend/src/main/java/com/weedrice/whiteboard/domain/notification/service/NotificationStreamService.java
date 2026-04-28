package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.NotificationResponse;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import org.springframework.stereotype.Service;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Map;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

@Service
class NotificationStreamService {

    private final Map<Long, Map<String, SseEmitter>> emitters = new ConcurrentHashMap<>();

    SseEmitter subscribe(Long userId) {
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
        } catch (IOException | RuntimeException e) {
            removeEmitter(userId, connectionId);
        }

        return emitter;
    }

    void sendHeartbeat() {
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
                } catch (IOException | RuntimeException e) {
                    removeEmitter(userId, entry.getKey());
                }
            }
        }
    }

    void deliverNotification(Long userId, Notification notification) {
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
            } catch (IOException | RuntimeException e) {
                removeEmitter(userId, entry.getKey());
            }
        }
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
}
