package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;
import java.time.temporal.ChronoUnit;
import java.util.List;
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
    private final FileVariantRepository fileVariantRepository;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;
    private final EmoticonFileReferenceService emoticonFileReferenceService;
    private final Clock clock;
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
            snapshot = snapshot.withVariantFilePaths(fileVariantRepository.findByFileFileId(snapshot.fileId()).stream()
                    .map(variant -> variant.getFilePath())
                    .toList());

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
        LocalDateTime claimedAt = now();
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
                    if (!emoticonFileReferenceService.isReferenced(current.getFileId())) {
                        return false;
                    }
                    current.cancelDeletionRequest();
                    return true;
                })
                .orElse(false)));
    }

    private boolean deleteFromStorage(FileDeletionSnapshot snapshot) {
        try {
            for (String variantFilePath : snapshot.variantFilePaths()) {
                fileStorageService.deleteFileOrThrow(variantFilePath);
            }
            fileStorageService.deleteFileOrThrow(snapshot.filePath());
            return true;
        } catch (RuntimeException e) {
            log.warn("Failed to delete file from storage. fileId={}", snapshot.fileId(), e);
            markDeletionFailed(snapshot, e);
            return false;
        }
    }

    private void markDeletionFailed(FileDeletionSnapshot snapshot, RuntimeException cause) {
        LocalDateTime deleteRequestedAt = now();
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(snapshot.fileId())
                .ifPresent(current -> {
                    if (snapshot.matches(current)) {
                        current.markDeletionFailed(cause.getMessage(), deleteRequestedAt);
                    }
                }));
    }

    private LocalDateTime now() {
        return LocalDateTime.now(clock).truncatedTo(ChronoUnit.MILLIS);
    }

    private void finalizeDeletion(FileDeletionSnapshot snapshot) {
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(snapshot.fileId())
                .ifPresent(current -> {
                    if (!snapshot.matches(current)) {
                        return;
                    }
                    if (current.isDeletionRequested()) {
                        fileVariantRepository.deleteByFileFileId(current.getFileId());
                        fileRepository.delete(current);
                    }
                }));
    }

    private record FileDeletionSnapshot(
            Long fileId,
            String filePath,
            Long relatedId,
            String relatedType,
            List<String> variantFilePaths,
            LocalDateTime claimedAt,
            boolean staleProcessingClaim) {

        private static FileDeletionSnapshot from(File file, LocalDateTime claimedAt, boolean staleProcessingClaim) {
            return new FileDeletionSnapshot(
                    file.getFileId(),
                    file.getFilePath(),
                    file.getRelatedId(),
                    file.getRelatedType(),
                    List.of(),
                    claimedAt,
                    staleProcessingClaim);
        }

        private FileDeletionSnapshot withVariantFilePaths(List<String> variantFilePaths) {
            return new FileDeletionSnapshot(
                    fileId,
                    filePath,
                    relatedId,
                    relatedType,
                    variantFilePaths == null ? List.of() : List.copyOf(variantFilePaths),
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
