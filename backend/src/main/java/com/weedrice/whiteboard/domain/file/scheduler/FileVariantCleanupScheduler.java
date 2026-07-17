package com.weedrice.whiteboard.domain.file.scheduler;

import com.weedrice.whiteboard.domain.file.service.FileVariantCleanupWorker;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileVariantCleanupScheduler {

    private final FileVariantCleanupWorker cleanupWorker;

    @Scheduled(cron = "0 20 * * * ?")
    public void cleanupStaleVariants() {
        int deleted = cleanupWorker.cleanupStaleVariants();
        if (deleted > 0) {
            log.info("Cleaned {} stale image variant(s)", deleted);
        }
    }
}
