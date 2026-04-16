package com.weedrice.whiteboard.domain.file.scheduler;

import com.weedrice.whiteboard.domain.file.service.FileDeletionWorker;
import com.weedrice.whiteboard.domain.file.service.FileService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.core.task.TaskRejectedException;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class FileDeletionScheduler {

    private static final int PENDING_DELETE_BATCH_SIZE = 40;
    private static final int RETRY_FAILED_BATCH_SIZE = 10;

    private final FileService fileService;
    private final FileDeletionWorker fileDeletionWorker;

    @Scheduled(fixedDelay = 60_000L, initialDelay = 60_000L)
    public void processPendingDeletions() {
        var fileIds = new java.util.ArrayList<Long>();
        fileIds.addAll(fileService.getPendingDeletionFileIds(PENDING_DELETE_BATCH_SIZE));
        fileIds.addAll(fileService.getRetryableFailedDeletionFileIds(RETRY_FAILED_BATCH_SIZE));
        if (fileIds.isEmpty()) {
            return;
        }

        log.info("Starting file deletion worker batch. size={}", fileIds.size());
        for (Long fileId : fileIds) {
            if (fileDeletionWorker.tryClaim(fileId)) {
                try {
                    fileDeletionWorker.processDeletion(fileId);
                } catch (TaskRejectedException e) {
                    fileDeletionWorker.releaseClaim(fileId);
                    log.warn("File deletion task submission was rejected. fileId={}", fileId, e);
                }
            }
        }
    }
}
