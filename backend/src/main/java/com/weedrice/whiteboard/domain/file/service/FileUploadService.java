package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.service.FileUploadValidationPolicy.ValidatedUpload;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FileUploadService {

    private final UserWritableResolver userWritableResolver;
    private final FileStorageService fileStorageService;
    private final FileUploadValidationPolicy validationPolicy;
    private final FileUploadStateCommand stateCommand;
    private final FileImageVariantGenerator imageVariantGenerator;

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileUploadResponse uploadFile(Long uploaderId, MultipartFile multipartFile) {
        File file = processUpload(uploaderId, multipartFile);
        return FileUploadResponse.from(file);
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public FileSimpleResponse uploadSimpleFile(Long uploaderId, MultipartFile multipartFile) {
        File file = processUpload(uploaderId, multipartFile);
        return FileSimpleResponse.from(file);
    }

    private File processUpload(Long uploaderId, MultipartFile multipartFile) {
        ValidatedUpload validatedUpload = validationPolicy.validate(multipartFile);
        User uploader = userWritableResolver.resolve(uploaderId);
        String storedFileName = fileStorageService.generateStoredFileName(validatedUpload.originalFilename());
        File pendingUploadFile = stateCommand.createPendingUploadRecord(
                storedFileName,
                validatedUpload.originalFilename(),
                validatedUpload.fileSize(),
                validatedUpload.detectedMimeType(),
                uploader);
        try {
            fileStorageService.storeFileAs(multipartFile, validatedUpload.detectedMimeType(), storedFileName);
            imageVariantGenerator.generateVariants(pendingUploadFile, multipartFile, validatedUpload.detectedMimeType());
            return stateCommand.completePendingUpload(pendingUploadFile.getFileId());
        } catch (Exception e) {
            try {
                stateCommand.requestPendingUploadDeletion(pendingUploadFile.getFileId());
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
    }
}
