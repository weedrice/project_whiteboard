package com.weedrice.whiteboard.domain.file.scheduler;

import com.weedrice.whiteboard.domain.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileCleanupScheduler {

    private final FileService fileService;

    @Scheduled(cron = "0 0 2 * * ?")
    public void cleanupTemporaryFiles() {
        log.info("Starting temporary file cleanup scheduler");
        fileService.cleanUpTemporaryFiles();
        log.info("Finished temporary file cleanup scheduler");
    }
}
