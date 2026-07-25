package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.global.common.util.FileExtensionUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;
import org.springframework.web.multipart.MultipartFile;

import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Iterator;
import javax.imageio.ImageIO;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;

@Component
class FileUploadValidationPolicy {

    private static final int MAX_ORIGINAL_FILENAME_LENGTH = 255;
    private static final int IMAGE_SIGNATURE_READ_BYTES = 12;
    /**
     * 전역 상한. {@code spring.servlet.multipart.max-file-size}와 같은 값이어야 하며,
     * 그쪽이 먼저 걸리면 {@code MaxUploadSizeExceededException}으로 처리된다.
     * 대상별 상한은 {@link FileUploadTarget}에 둔다.
     */
    private static final long MAX_FILE_SIZE = 10 * 1024 * 1024;
    static final int MAX_IMAGE_DIMENSION = 16_384;
    static final long MAX_IMAGE_PIXELS = 50_000_000L;

    ValidatedUpload validate(MultipartFile multipartFile) {
        return validate(multipartFile, FileUploadTarget.GENERIC);
    }

    ValidatedUpload validate(MultipartFile multipartFile, FileUploadTarget target) {
        FileUploadTarget safeTarget = target != null ? target : FileUploadTarget.GENERIC;
        if (multipartFile.isEmpty()) {
            throw new BusinessException(ErrorCode.FILE_EMPTY);
        }
        // 대상별 상한과 전역 상한 중 더 엄격한 쪽을 적용한다.
        long sizeLimit = Math.min(safeTarget.getMaxSizeBytes(), MAX_FILE_SIZE);
        if (multipartFile.getSize() > sizeLimit) {
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

        String extension = FileExtensionUtils.extractLowerCaseExtension(originalFilename);
        if (!isExtensionCompatible(extension, detectedMimeType)) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }

        ImageMetadata metadata = readImageMetadata(multipartFile);
        validateImageDimensions(metadata);
        validateTargetDimensions(metadata, safeTarget);
        return new ValidatedUpload(
                originalFilename,
                detectedMimeType,
                multipartFile.getSize(),
                metadata.formatName(),
                metadata.width(),
                metadata.height());
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

    private ImageMetadata readImageMetadata(MultipartFile multipartFile) {
        try (InputStream inputStream = multipartFile.getInputStream();
                ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            if (imageInputStream == null) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                return new ImageMetadata(reader.getFormatName(), reader.getWidth(0), reader.getHeight(0));
            } finally {
                reader.dispose();
            }
        } catch (BusinessException e) {
            throw e;
        } catch (IOException | RuntimeException e) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    /** 대상이 해상도 상한을 두었다면 전역 상한과 별개로 한 번 더 확인한다. */
    private void validateTargetDimensions(ImageMetadata metadata, FileUploadTarget target) {
        if (!target.hasDimensionLimit()) {
            return;
        }
        if (metadata.width() > target.getMaxWidth() || metadata.height() > target.getMaxHeight()) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private void validateImageDimensions(ImageMetadata metadata) {
        if (metadata.width() <= 0
                || metadata.height() <= 0
                || metadata.width() > MAX_IMAGE_DIMENSION
                || metadata.height() > MAX_IMAGE_DIMENSION
                || (long) metadata.width() * metadata.height() > MAX_IMAGE_PIXELS) {
            throw new BusinessException(ErrorCode.INVALID_FILE_TYPE);
        }
    }

    private record ImageMetadata(String formatName, int width, int height) {
    }

    record ValidatedUpload(
            String originalFilename,
            String detectedMimeType,
            Long fileSize,
            String imageFormat,
            int width,
            int height) {
    }
}
