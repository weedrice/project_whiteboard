package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.ConcurrentHashMap;

@Slf4j
@Service
@RequiredArgsConstructor
public class FileDeletionWorker {

    private static final int MAX_DELETE_RETRY_COUNT = 5;
    private static final int DELETE_CLAIM_STALE_MINUTES = 30;

    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final EmoticonImageRepository emoticonImageRepository;
    private final EmoticonMasterRepository emoticonMasterRepository;
    private final Set<Long> processingFileIds = ConcurrentHashMap.newKeySet();

    public boolean tryClaim(Long fileId) {
        // Local duplicate throttle only. The DELETING state transition is the cross-node claim.
        return processingFileIds.add(fileId);
    }

    public void releaseClaim(Long fileId) {
        processingFileIds.remove(fileId);
    }

    @Async("taskExecutor")
    public void processDeletion(Long fileId) {
        try {
            FileDeletionSnapshot snapshot = claimDeletion(fileId);
            if (snapshot == null) {
                return;
            }
            if (cancelIfReferencedBeforeStorage(snapshot)) {
                return;
            }

            if (!deleteFromStorage(snapshot)) {
                return;
            }
            finalizeDeletion(snapshot);
        } catch (RuntimeException e) {
            log.warn("Failed to finalize deleted file. fileId={}", fileId, e);
        } finally {
            releaseClaim(fileId);
        }
    }

    private FileDeletionSnapshot claimDeletion(Long fileId) {
        LocalDateTime claimedAt = LocalDateTime.now().truncatedTo(ChronoUnit.MILLIS);
        LocalDateTime staleBefore = claimedAt.minusMinutes(DELETE_CLAIM_STALE_MINUTES);
        return transactionTemplate.execute(status -> fileRepository
                .findDeletionClaimCandidateForUpdate(fileId, MAX_DELETE_RETRY_COUNT, staleBefore)
                .map(file -> {
                    boolean staleProcessingClaim = file.getStorageStatus() == FileStorageStatus.DELETING;
                    file.markDeleting(claimedAt);
                    return FileDeletionSnapshot.from(file, claimedAt, staleProcessingClaim);
                })
                .orElse(null));
    }

    private boolean cancelIfReferencedBeforeStorage(FileDeletionSnapshot snapshot) {
        return Boolean.TRUE.equals(transactionTemplate.execute(status -> fileRepository
                .findByIdForUpdate(snapshot.fileId())
                .map(current -> {
                    if (!snapshot.matches(current) || snapshot.staleProcessingClaim()) {
                        return false;
                    }
                    if (!isReferencedByEmoticon(current)) {
                        return false;
                    }
                    current.cancelDeletionRequest();
                    return true;
                })
                .orElse(false)));
    }

    private boolean deleteFromStorage(FileDeletionSnapshot snapshot) {
        try {
            fileStorageService.deleteFileOrThrow(snapshot.filePath());
            return true;
        } catch (RuntimeException e) {
            log.warn("Failed to delete file from storage. fileId={}", snapshot.fileId(), e);
            markDeletionFailed(snapshot, e);
            return false;
        }
    }

    private boolean isReferencedByEmoticon(File file) {
        if (file.getFileId() == null) {
            return false;
        }
        var candidateUrls = FileUrlResolver.referenceCandidates(file.getFileId());
        return emoticonImageRepository.existsByImageUrlIn(candidateUrls)
                || emoticonMasterRepository.existsByThumbnailUrlIn(candidateUrls);
    }

    private void markDeletionFailed(FileDeletionSnapshot snapshot, RuntimeException cause) {
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(snapshot.fileId())
                .ifPresent(current -> {
                    if (snapshot.matches(current)) {
                        current.markDeletionFailed(cause.getMessage());
                    }
                }));
    }

    private void finalizeDeletion(FileDeletionSnapshot snapshot) {
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(snapshot.fileId())
                .ifPresent(current -> {
                    if (!snapshot.matches(current)) {
                        return;
                    }
                    if (current.isDeletionRequested()) {
                        fileRepository.delete(current);
                    }
                }));
    }

    private record FileDeletionSnapshot(
            Long fileId,
            String filePath,
            Long relatedId,
            String relatedType,
            LocalDateTime claimedAt,
            boolean staleProcessingClaim) {

        private static FileDeletionSnapshot from(File file, LocalDateTime claimedAt, boolean staleProcessingClaim) {
            return new FileDeletionSnapshot(
                    file.getFileId(),
                    file.getFilePath(),
                    file.getRelatedId(),
                    file.getRelatedType(),
                    claimedAt,
                    staleProcessingClaim);
        }

        private boolean matches(File file) {
            return file != null
                    && file.getStorageStatus() == FileStorageStatus.DELETING
                    && Objects.equals(fileId, file.getFileId())
                    && Objects.equals(filePath, file.getFilePath())
                    && Objects.equals(relatedId, file.getRelatedId())
                    && Objects.equals(relatedType, file.getRelatedType())
                    && Objects.equals(claimedAt, file.getDeleteRequestedAt());
        }
    }
}
