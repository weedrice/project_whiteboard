package com.weedrice.whiteboard.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
class NotificationDeliveryJobKickoff {

    private final NotificationDeliveryJobProcessor processor;

    @Async("notificationTaskExecutor")
    public void process(Long jobId) {
        processor.processJob(jobId);
    }
}
