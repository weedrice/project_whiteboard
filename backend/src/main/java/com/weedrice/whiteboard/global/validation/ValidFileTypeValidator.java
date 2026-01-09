package com.weedrice.whiteboard.global.validation;

import jakarta.validation.ConstraintValidator;
import jakarta.validation.ConstraintValidatorContext;
import org.springframework.web.multipart.MultipartFile;

import java.util.Arrays;
import java.util.Locale;

/**
 * 파일 타입 검증 Validator
 */
public class ValidFileTypeValidator implements ConstraintValidator<ValidFileType, MultipartFile> {

    private String[] allowedMimeTypes;
    private String[] allowedExtensions;
    private boolean imageOnly;
    private long maxSize;

    @Override
    public void initialize(ValidFileType constraintAnnotation) {
        this.allowedMimeTypes = constraintAnnotation.allowedMimeTypes();
        this.allowedExtensions = constraintAnnotation.allowedExtensions();
        this.imageOnly = constraintAnnotation.imageOnly();
        this.maxSize = constraintAnnotation.maxSize();
    }

    @Override
    public boolean isValid(MultipartFile file, ConstraintValidatorContext context) {
        if (file == null || file.isEmpty()) {
            return true; // null/empty는 다른 검증에서 처리
        }

        // 파일 크기 검증
        if (file.getSize() > maxSize) {
            context.disableDefaultConstraintViolation();
            context.buildConstraintViolationWithTemplate(
                    String.format("파일 크기는 %dMB 이하여야 합니다", maxSize / (1024 * 1024))
            ).addConstraintViolation();
            return false;
        }

        String mimeType = file.getContentType();
        String originalFilename = file.getOriginalFilename();

        // MIME 타입 검증
        if (mimeType != null) {
            // 이미지 파일만 허용하는 경우
            if (imageOnly && !mimeType.startsWith("image/")) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate("이미지 파일만 업로드 가능합니다").addConstraintViolation();
                return false;
            }

            // 허용된 MIME 타입 목록이 있는 경우
            if (allowedMimeTypes.length > 0) {
                boolean isAllowed = Arrays.stream(allowedMimeTypes)
                        .anyMatch(allowed -> mimeType.equalsIgnoreCase(allowed));
                if (!isAllowed) {
                    context.disableDefaultConstraintViolation();
                    context.buildConstraintViolationWithTemplate(
                            String.format("허용된 파일 형식: %s", String.join(", ", allowedMimeTypes))
                    ).addConstraintViolation();
                    return false;
                }
            }
        }

        // 확장자 검증
        if (originalFilename != null && allowedExtensions.length > 0) {
            String extension = getFileExtension(originalFilename);
            boolean isAllowed = Arrays.stream(allowedExtensions)
                    .anyMatch(allowed -> extension.equalsIgnoreCase(allowed));
            if (!isAllowed) {
                context.disableDefaultConstraintViolation();
                context.buildConstraintViolationWithTemplate(
                        String.format("허용된 파일 확장자: %s", String.join(", ", allowedExtensions))
                ).addConstraintViolation();
                return false;
            }
        }

        return true;
    }

    private String getFileExtension(String filename) {
        int lastDotIndex = filename.lastIndexOf('.');
        if (lastDotIndex == -1) {
            return "";
        }
        return filename.substring(lastDotIndex).toLowerCase(Locale.ROOT);
    }
}
