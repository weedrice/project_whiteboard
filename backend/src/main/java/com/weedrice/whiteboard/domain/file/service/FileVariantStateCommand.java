package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class FileVariantStateCommand {

    private final FileVariantRepository fileVariantRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileVariant createPending(
            File file,
            FileVariantType variantType,
            String filePath,
            long fileSize,
            String mimeType,
            int width,
            int height) {
        return fileVariantRepository.saveAndFlush(FileVariant.builder()
                .file(file)
                .variantType(variantType)
                .filePath(filePath)
                .fileSize(fileSize)
                .mimeType(mimeType)
                .width(width)
                .height(height)
                .storageStatus(FileStorageStatus.PENDING_UPLOAD)
                .build());
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void activate(Long fileVariantId) {
        FileVariant variant = fileVariantRepository.findByIdForUpdate(fileVariantId)
                .orElseThrow(() -> new IllegalStateException("File variant upload intent disappeared"));
        if (variant.getStorageStatus() != FileStorageStatus.PENDING_UPLOAD) {
            throw new IllegalStateException("File variant upload intent is no longer pending");
        }
        variant.activate();
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileVariantCleanupSnapshot claimCleanup(
            Long fileVariantId, LocalDateTime cutoff, LocalDateTime now, int maxRetryCount) {
        return fileVariantRepository.findByIdForUpdate(fileVariantId)
                .filter(variant -> variant.getStorageStatus() == FileStorageStatus.PENDING_DELETE
                        || (variant.getStorageStatus() == FileStorageStatus.PENDING_UPLOAD
                        && variant.getCreatedAt().isBefore(cutoff)))
                .filter(variant -> variant.getCleanupRetryCount() == null
                        || variant.getCleanupRetryCount() < maxRetryCount)
                .filter(variant -> variant.getCleanupNextAttemptAt() == null
                        || !variant.getCleanupNextAttemptAt().isAfter(now))
                .map(variant -> {
                    variant.requestDeletion();
                    return new FileVariantCleanupSnapshot(
                            variant.getFileVariantId(), variant.getFilePath(),
                            variant.getCleanupRetryCount() == null ? 0 : variant.getCleanupRetryCount());
                })
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void deleteClaimed(Long fileVariantId) {
        fileVariantRepository.findByIdForUpdate(fileVariantId)
                .filter(variant -> variant.getStorageStatus() == FileStorageStatus.PENDING_DELETE)
                .ifPresent(fileVariantRepository::delete);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void recordCleanupFailure(Long fileVariantId, String error, LocalDateTime nextAttemptAt) {
        fileVariantRepository.findByIdForUpdate(fileVariantId)
                .filter(variant -> variant.getStorageStatus() == FileStorageStatus.PENDING_DELETE)
                .ifPresent(variant -> variant.recordCleanupFailure(error, nextAttemptAt));
    }

    record FileVariantCleanupSnapshot(Long fileVariantId, String filePath, int retryCount) {
    }
}
