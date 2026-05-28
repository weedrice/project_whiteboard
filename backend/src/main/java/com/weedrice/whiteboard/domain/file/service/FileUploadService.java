package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileSimpleResponse;
import com.weedrice.whiteboard.domain.file.dto.FileUploadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileStorageStatus;
import com.weedrice.whiteboard.domain.file.repository.FileRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionTemplate;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class FileUploadService {

    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;
    private static final int IMAGE_SIGNATURE_READ_BYTES = 12;

    private final FileRepository fileRepository;
    private final UserWritableResolver userWritableResolver;
    private final FileStorageService fileStorageService;
    private final TransactionTemplate transactionTemplate;

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
        if (multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        if (multipartFile.getSize() > 10 * 1024 * 1024) {
            throw new BusinessException(ErrorCode.FILE_TOO_LARGE);
        }

        String declaredMimeType = multipartFile.getContentType();
        String originalFilename = normalizeOriginalFilename(multipartFile.getOriginalFilename());

        String detectedMimeType = detectImageMimeType(multipartFile);
        if (detectedMimeType == null) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        if (declaredMimeType != null && !isDeclaredMimeCompatible(declaredMimeType, detectedMimeType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        String extension = getFileExtension(originalFilename);
        if (!isExtensionCompatible(extension, detectedMimeType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        User uploader = userWritableResolver.resolve(uploaderId);
        String storedFileName = fileStorageService.generateStoredFileName(originalFilename);
        File pendingUploadFile = createPendingUploadRecord(
                storedFileName,
                originalFilename,
                multipartFile.getSize(),
                detectedMimeType,
                uploader);
        try {
            fileStorageService.storeFileAs(multipartFile, detectedMimeType, storedFileName);
            return completePendingUpload(pendingUploadFile.getFileId());
        } catch (Exception e) {
            try {
                requestPendingUploadDeletion(pendingUploadFile.getFileId());
            } catch (Exception cleanupException) {
                e.addSuppressed(cleanupException);
            }
            throw e;
        }
    }

    private File createPendingUploadRecord(
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

    private File completePendingUpload(Long fileId) {
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

    private void requestPendingUploadDeletion(Long fileId) {
        transactionTemplate.executeWithoutResult(status -> fileRepository.findByIdForUpdate(fileId)
                .ifPresent(File::markDeletionPending));
    }

    private String normalizeOriginalFilename(String originalFilename) {
        if (!StringUtils.hasText(originalFilename)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

        String normalizedFilename = StringUtils.getFilename(StringUtils.cleanPath(originalFilename));
        if (!StringUtils.hasText(normalizedFilename)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        if (normalizedFilename.length() > MAX_ORIGINAL_FILENAME_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalizedFilename;
    }

    private String getFileExtension(String filename) {
        if (filename == null) {
            return "";
        }
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase();
    }

    private String detectImageMimeType(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream()) {
            byte[] data = inputStream.readNBytes(IMAGE_SIGNATURE_READ_BYTES);
            if (isJpeg(data)) {
                return "image/jpeg";
            }
            if (isPng(data)) {
                return "image/png";
            }
            if (isGif(data)) {
                return "image/gif";
            }
            if (isWebp(data)) {
                return "image/webp";
            }
            return null;
        } catch (IOException e) {
            throw new BusinessException(ErrorCode.FILE_UPLOAD_ERROR);
        }
    }

    private boolean isDeclaredMimeCompatible(String declaredMimeType, String detectedMimeType) {
        if ("image/jpg".equalsIgnoreCase(declaredMimeType) && "image/jpeg".equalsIgnoreCase(detectedMimeType)) {
            return true;
        }
        return detectedMimeType.equalsIgnoreCase(declaredMimeType);
    }

    private boolean isExtensionCompatible(String extension, String detectedMimeType) {
        return switch (detectedMimeType) {
            case "image/jpeg" -> Arrays.asList(".jpg", ".jpeg").contains(extension);
            case "image/png" -> ".png".equals(extension);
            case "image/gif" -> ".gif".equals(extension);
            case "image/webp" -> ".webp".equals(extension);
            default -> false;
        };
    }

    private boolean isJpeg(byte[] data) {
        return data.length >= 3
                && (data[0] & 0xFF) == 0xFF
                && (data[1] & 0xFF) == 0xD8
                && (data[2] & 0xFF) == 0xFF;
    }

    private boolean isPng(byte[] data) {
        byte[] pngSignature = new byte[] {
                (byte) 0x89, 0x50, 0x4E, 0x47, 0x0D, 0x0A, 0x1A, 0x0A
        };
        if (data.length < pngSignature.length) {
            return false;
        }
        for (int i = 0; i < pngSignature.length; i++) {
            if (data[i] != pngSignature[i]) {
                return false;
            }
        }
        return true;
    }

    private boolean isGif(byte[] data) {
        return data.length >= 6
                && data[0] == 'G'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == '8'
                && (data[4] == '7' || data[4] == '9')
                && data[5] == 'a';
    }

    private boolean isWebp(byte[] data) {
        return data.length >= 12
                && data[0] == 'R'
                && data[1] == 'I'
                && data[2] == 'F'
                && data[3] == 'F'
                && data[8] == 'W'
                && data[9] == 'E'
                && data[10] == 'B'
                && data[11] == 'P';
    }
}
