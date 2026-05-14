package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonImageRepository;
import com.weedrice.whiteboard.domain.emoticon.repository.EmoticonMasterRepository;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileRepository.FileCleanupCandidateProjection;
import com.weedrice.whiteboard.domain.file.support.FileUrlResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

@Service
@RequiredArgsConstructor
public class FileTemporaryCleanupWorker {

    private final FileRepository fileRepository;
    private final EmoticonImageRepository emoticonImageRepository;
    private final EmoticonMasterRepository emoticonMasterRepository;

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
        List<FileCleanupCandidateProjection> candidates = fileRepository.findTemporaryFileCleanupCandidatesAfter(
                cutoffCreatedAt,
                lastCreatedAt,
                lastFileId,
                PageRequest.of(0, batchSize));
        if (candidates.isEmpty()) {
            return CleanupBatchResult.completed();
        }

        List<Long> temporaryFileIds = candidates.stream()
                .map(FileCleanupCandidateProjection::getFileId)
                .toList();
        List<Long> cleanupFileIds = excludeEmoticonReferencedFileIds(temporaryFileIds);
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

    private List<Long> excludeEmoticonReferencedFileIds(List<Long> fileIds) {
        if (fileIds == null || fileIds.isEmpty()) {
            return List.of();
        }

        Map<String, Long> fileIdsByUrl = new LinkedHashMap<>();
        for (Long fileId : fileIds) {
            for (String candidateUrl : FileUrlResolver.referenceCandidates(fileId)) {
                fileIdsByUrl.put(candidateUrl, fileId);
            }
        }

        Set<Long> referencedFileIds = new LinkedHashSet<>();
        List<String> candidateUrls = fileIdsByUrl.keySet().stream().toList();
        List<String> imageUrls = emoticonImageRepository.findReferencedImageUrls(candidateUrls);
        if (imageUrls != null) {
            imageUrls.forEach(url -> referencedFileIds.add(fileIdsByUrl.get(url)));
        }
        List<String> thumbnailUrls = emoticonMasterRepository.findReferencedThumbnailUrls(candidateUrls);
        if (thumbnailUrls != null) {
            thumbnailUrls.forEach(url -> referencedFileIds.add(fileIdsByUrl.get(url)));
        }

        return fileIds.stream()
                .filter(fileId -> !referencedFileIds.contains(fileId))
                .toList();
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
