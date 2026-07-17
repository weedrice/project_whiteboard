package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

import java.time.Clock;
import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
class FileUploadStateCommand {

    static final int MAX_TEMPORARY_FILE_COUNT = 100;
    static final long MAX_TEMPORARY_FILE_BYTES = 500L * 1024L * 1024L;

    private final FileRepository fileRepository;
    private final UserRepository userRepository;
    private final UserWritableResolver userWritableResolver;
    private final TransactionTemplate transactionTemplate;
    private final Clock clock;

    File createPendingUploadRecord(
            String storedFileName,
            String originalFilename,
            Long fileSize,
            String mimeType,
            Integer imageWidth,
            Integer imageHeight,
            Integer expectedVariantCount,
            Long uploaderId) {
        return transactionTemplate.execute(status -> {
            User uploader = userRepository.findByIdForUpdate(uploaderId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            userWritableResolver.validateWritable(uploader);
            FileRepository.TemporaryUploadUsageProjection usage =
                    fileRepository.findTemporaryUploadUsage(uploaderId);
            long currentCount = usage == null ? 0 : usage.getFileCount();
            long currentBytes = usage == null ? 0 : usage.getTotalBytes();
            if (currentCount >= MAX_TEMPORARY_FILE_COUNT
                    || fileSize > MAX_TEMPORARY_FILE_BYTES - currentBytes) {
                throw new BusinessException(ErrorCode.INVALID_INPUT_VALUE);
            }
            File file = File.builder()
                    .filePath(storedFileName)
                    .originalName(originalFilename)
                    .fileSize(fileSize)
                    .mimeType(mimeType)
                    .imageWidth(imageWidth)
                    .imageHeight(imageHeight)
                    .expectedVariantCount(expectedVariantCount)
                    .uploader(uploader)
                    .storageStatus(FileStorageStatus.PENDING_UPLOAD)
                    .build();

            return fileRepository.save(file);
        });
    }

    void markVariantReconciled(Long fileId, int width, int height, int expectedCount, int version) {
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(fileId)
                .ifPresent(file -> file.markVariantReconciled(width, height, expectedCount, version)));
    }

    File completePendingUpload(Long fileId) {
        return transactionTemplate.execute(status -> {
            File file = fileRepository.findByIdForUpdate(fileId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
            if (file.getStorageStatus() != FileStorageStatus.PENDING_UPLOAD) {
                throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
            }
            file.markUploadCompleted();
            return file;
        });
    }

    void requestPendingUploadDeletion(Long fileId) {
        LocalDateTime deleteRequestedAt = LocalDateTime.now(clock);
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(fileId)
                .ifPresent(file -> file.markDeletionPending(deleteRequestedAt)));
    }
}
