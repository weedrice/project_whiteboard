package com.weedrice.whiteboard.domain.notification.service;

import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

@Service
@Transactional(readOnly = true)
class NotificationSseFacade {

    private final NotificationStreamService streamService;

    NotificationSseFacade(NotificationStreamService streamService) {
        this.streamService = streamService;
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public SseEmitter subscribe(Long userId) {
        return streamService.subscribe(userId);
    }

    @Scheduled(fixedRate = 25_000)
    public void sendHeartbeat() {
        streamService.sendHeartbeat();
    }
}
