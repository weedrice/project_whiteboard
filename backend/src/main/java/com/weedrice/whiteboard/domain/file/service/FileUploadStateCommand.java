package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.support.TransactionTemplate;

@Component
@RequiredArgsConstructor
class FileUploadStateCommand {

    private final FileRepository fileRepository;
    private final TransactionTemplate transactionTemplate;

    File createPendingUploadRecord(
            String storedFileName,
            String originalFilename,
            Long fileSize,
            String mimeType,
            User uploader) {
        return transactionTemplate.execute(status -> {
            File file = File.builder()
                    .filePath(storedFileName)
                    .originalName(originalFilename)
                    .fileSize(fileSize)
                    .mimeType(mimeType)
                    .uploader(uploader)
                    .storageStatus(FileStorageStatus.PENDING_UPLOAD)
                    .build();

            return fileRepository.save(file);
        });
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
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(fileId)
                .ifPresent(File::markDeletionPending));
    }
}
