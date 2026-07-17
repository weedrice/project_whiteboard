package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileVariantCleanupWorker {

    private static final int BATCH_SIZE = 100;
    private static final int PENDING_UPLOAD_GRACE_HOURS = 2;

    private final FileVariantRepository fileVariantRepository;
    private final FileVariantStateCommand stateCommand;
    private final FileStorageService fileStorageService;
    private final Clock clock;

    public int cleanupStaleVariants() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(PENDING_UPLOAD_GRACE_HOURS);
        List<Long> candidateIds = fileVariantRepository.findCleanupCandidateIds(
                cutoff,
                PageRequest.of(0, BATCH_SIZE));
        int deleted = 0;
        for (Long candidateId : candidateIds) {
            FileVariantStateCommand.FileVariantCleanupSnapshot snapshot = stateCommand.claimCleanup(candidateId, cutoff);
            if (snapshot == null) {
                continue;
            }
            try {
                fileStorageService.deleteFileOrThrow(snapshot.filePath());
                stateCommand.deleteClaimed(snapshot.fileVariantId());
                deleted++;
            } catch (RuntimeException exception) {
                log.warn("Failed to clean stale image variant. fileVariantId={}, exceptionType={}",
                        snapshot.fileVariantId(), exception.getClass().getSimpleName());
            }
        }
        return deleted;
    }
}
