package com.weedrice.whiteboard.domain.notification.scheduler;

import com.weedrice.whiteboard.domain.notification.service.PushDeliveryJobProcessor;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PushDeliveryJobScheduler {

    private final PushDeliveryJobProcessor processor;

    @Scheduled(fixedDelay = 5_000, scheduler = "webPushTaskScheduler")
    public void processPendingJobs() {
        int processed = processor.processDueJobs();
        if (processed > 0) {
            log.info("Processed {} web push delivery job(s)", processed);
        }
    }
}
