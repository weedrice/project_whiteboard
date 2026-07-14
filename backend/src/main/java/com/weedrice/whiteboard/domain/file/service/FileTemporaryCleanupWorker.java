package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileRepository.FileCleanupCandidateProjection;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class FileTemporaryCleanupWorker {

    private final FileRepository fileRepository;
    private final EmoticonFileReferenceService emoticonFileReferenceService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int requestPendingUploadDeletion(LocalDateTime cutoffCreatedAt, LocalDateTime deleteRequestedAt) {
        return fileRepository.requestDeletionForStalePendingUploads(cutoffCreatedAt, deleteRequestedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public CleanupBatchResult requestDeletionBatch(
            LocalDateTime cutoffCreatedAt,
            LocalDateTime lastCreatedAt,
            Long lastFileId,
            int batchSize,
            LocalDateTime deleteRequestedAt) {
        PageRequest pageRequest = PageRequest.of(0, batchSize);
        List<FileCleanupCandidateProjection> candidates = lastCreatedAt == null
                ? fileRepository.findTemporaryFileCleanupCandidates(cutoffCreatedAt, pageRequest)
                : fileRepository.findTemporaryFileCleanupCandidatesAfter(
                        cutoffCreatedAt,
                        lastCreatedAt,
                        lastFileId,
                        pageRequest);
        if (candidates.isEmpty()) {
            return CleanupBatchResult.completed();
        }

        List<Long> temporaryFileIds = candidates.stream()
                .map(FileCleanupCandidateProjection::getFileId)
                .toList();
        List<Long> cleanupFileIds = emoticonFileReferenceService.excludeReferencedFileIds(temporaryFileIds);
        int requestedCount = cleanupFileIds.isEmpty()
                ? 0
                : fileRepository.requestDeletionForTemporaryFiles(cleanupFileIds, deleteRequestedAt);

        FileCleanupCandidateProjection lastCandidate = candidates.get(candidates.size() - 1);
        return CleanupBatchResult.next(
                lastCandidate.getCreatedAt(),
                lastCandidate.getFileId(),
                candidates.size(),
                requestedCount);
    }

    record CleanupBatchResult(
            boolean finished,
            LocalDateTime lastCreatedAt,
            Long lastFileId,
            int candidateCount,
            int requestedCount) {

        static CleanupBatchResult completed() {
            return new CleanupBatchResult(true, null, null, 0, 0);
        }

        static CleanupBatchResult next(
                LocalDateTime lastCreatedAt,
                Long lastFileId,
                int candidateCount,
                int requestedCount) {
            return new CleanupBatchResult(false, lastCreatedAt, lastFileId, candidateCount, requestedCount);
        }
    }
}
