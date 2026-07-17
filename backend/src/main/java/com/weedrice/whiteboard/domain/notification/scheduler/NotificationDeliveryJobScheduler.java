package com.weedrice.whiteboard.domain.notification.scheduler;

import com.weedrice.whiteboard.domain.notification.service.NotificationDeliveryJobProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class NotificationDeliveryJobScheduler {

    private final NotificationDeliveryJobProcessor processor;

    @Scheduled(fixedDelay = 10_000)
    public void processPendingJobs() {
        int processed = processor.processDueJobs();
        if (processed > 0) {
            log.info("Processed {} notification delivery job(s)", processed);
        }
    }
}
