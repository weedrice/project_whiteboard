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

            fileStorageService.deleteFileOrThrow(file.getFilePath());
            transactionTemplate.executeWithoutResult(status -> fileRepository.findById(fileId).ifPresent(current -> {
                if (current.isDeletionRequested()) {
                    fileRepository.delete(current);
                }
            }));
        } catch (RuntimeException e) {
            log.warn("Failed to delete file from storage. fileId={}", fileId, e);
            transactionTemplate.executeWithoutResult(status -> fileRepository.findById(fileId).ifPresent(current -> {
                if (current.isDeletionRequested()) {
                    current.markDeletionFailed(e.getMessage());
                }
            }));
        } finally {
            releaseClaim(fileId);
        }
    }
}
