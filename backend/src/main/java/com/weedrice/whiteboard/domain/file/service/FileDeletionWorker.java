package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionWorker {

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final Set<Long> processingFileIds = ConcurrentHashMap.newKeySet();

    public boolean tryClaim(Long fileId) {
        return processingFileIds.add(fileId);
    }

    public void releaseClaim(Long fileId) {
        processingFileIds.remove(fileId);
    }

    @Async("taskExecutor")
    public void processDeletion(Long fileId) {
        try {
            File file = fileRepository.findById(fileId).orElse(null);
            if (file == null || !file.isDeletionRequested()) {
                return;
            }

            if (!deleteFromStorage(fileId, file)) {
                return;
            }
            finalizeDeletion(fileId);
        } catch (RuntimeException e) {
            log.warn("Failed to finalize deleted file. fileId={}", fileId, e);
        } finally {
            releaseClaim(fileId);
        }
    }

    private boolean deleteFromStorage(Long fileId, File file) {
        try {
            fileStorageService.deleteFileOrThrow(file.getFilePath());
            return true;
        } catch (RuntimeException e) {
            log.warn("Failed to delete file from storage. fileId={}", fileId, e);
            markDeletionFailed(fileId, e);
            return false;
        }
    }

    private void markDeletionFailed(Long fileId, RuntimeException cause) {
        transactionTemplate.executeWithoutResult(status -> fileRepository.findById(fileId).ifPresent(current -> {
            if (current.isDeletionRequested()) {
                current.markDeletionFailed(cause.getMessage());
            }
        }));
    }

    private void finalizeDeletion(Long fileId) {
        transactionTemplate.executeWithoutResult(status -> fileRepository.findById(fileId).ifPresent(current -> {
            if (current.isDeletionRequested()) {
                fileRepository.delete(current);
            }
        }));
    }
}
