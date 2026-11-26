package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class FileVariantStateCommand {

    private final FileVariantRepository fileVariantRepository;
    private final FileRepository fileRepository;
    private final FileStorageService fileStorageService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public FileVariant createPending(
            File file,
            FileVariantType variantType,
            String filePath,
            long fileSize,
            String mimeType,
            int width,
            int height) {
        File original = lockWritableOriginal(file.getFileId());
        return fileVariantRepository.saveAndFlush(FileVariant.builder()
                .file(original)
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
    public void storeAndActivate(Long fileId, Long fileVariantId, byte[] contents) {
        // Deletion claims the same original before collecting the object paths to remove.
        lockWritableOriginal(fileId);
        FileVariant variant = fileVariantRepository.findByIdForUpdate(fileVariantId)
                .orElseThrow(() -> new IllegalStateException("File variant upload intent disappeared"));
        if (!fileId.equals(variant.getFile().getFileId())
                || variant.getStorageStatus() != FileStorageStatus.PENDING_UPLOAD) {
            throw new IllegalStateException("File variant upload intent is no longer pending");
        }
        fileStorageService.storeBytesAs(contents, variant.getMimeType(), variant.getFilePath());
        variant.activate();
    }

    private File lockWritableOriginal(Long fileId) {
        return fileRepository.findByIdForUpdate(fileId)
                .filter(file -> file.getStorageStatus() == FileStorageStatus.ACTIVE
                        || file.getStorageStatus() == FileStorageStatus.PENDING_UPLOAD)
                .orElseThrow(() -> new IllegalStateException("Original file is no longer writable"));
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
                            variant.getFileVariantId(),
                            variant.getCleanupRetryCount() == null ? 0 : variant.getCleanupRetryCount());
                })
                .orElse(null);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean deleteClaimed(FileVariantCleanupSnapshot claim) {
        FileVariant variant = findClaimedForUpdate(claim).orElse(null);
        if (variant == null) return false;
        // A later generation can reuse the path only after this row is deleted and committed.
        fileStorageService.deleteFileOrThrow(variant.getFilePath());
        fileVariantRepository.delete(variant);
        return true;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public boolean recordCleanupFailure(FileVariantCleanupSnapshot claim, String error, LocalDateTime nextAttemptAt) {
        FileVariant variant = findClaimedForUpdate(claim).orElse(null);
        if (variant == null) return false;
        variant.recordCleanupFailure(error, nextAttemptAt);
        return true;
    }

    private Optional<FileVariant> findClaimedForUpdate(FileVariantCleanupSnapshot claim) {
        return fileVariantRepository.findByIdForUpdate(claim.fileVariantId())
                .filter(variant -> variant.getStorageStatus() == FileStorageStatus.PENDING_DELETE)
                .filter(variant -> (variant.getCleanupRetryCount() == null ? 0 : variant.getCleanupRetryCount())
                        == claim.retryCount());
    }

    record FileVariantCleanupSnapshot(Long fileVariantId, int retryCount) {
    }
}
