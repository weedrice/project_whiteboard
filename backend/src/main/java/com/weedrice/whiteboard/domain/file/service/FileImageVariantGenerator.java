package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

import javax.imageio.ImageIO;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;

@Slf4j
@Component
@RequiredArgsConstructor
class FileImageVariantGenerator {

    private static final String JPEG_MIME_TYPE = "image/jpeg";
    private static final String PNG_MIME_TYPE = "image/png";
    private static final String WEBP_MIME_TYPE = "image/webp";
    private static final String WEBP_FORMAT_NAME = "webp";

    private final FileStorageService fileStorageService;
    private final FileVariantRepository fileVariantRepository;

    public void generateVariants(File originalFile, MultipartFile multipartFile, String mimeType) {
        if (!isResizableMimeType(mimeType) || originalFile.getFileId() == null) {
            return;
        }

        try (InputStream inputStream = multipartFile.getInputStream()) {
            BufferedImage originalImage = ImageIO.read(inputStream);
            if (originalImage == null) {
                return;
            }
            for (FileVariantType variantType : FileVariantType.values()) {
                generateVariant(originalFile, originalImage, variantType);
            }
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to generate image variants. fileId={}", originalFile.getFileId(), e);
        }
    }

    private void generateVariant(
            File originalFile,
            BufferedImage originalImage,
            FileVariantType variantType) throws IOException {
        if (fileVariantRepository.findByFileFileIdAndVariantType(originalFile.getFileId(), variantType).isPresent()) {
            return;
        }

        ImageSize targetSize = resolveTargetSize(
                originalImage.getWidth(),
                originalImage.getHeight(),
                variantType.getMaxDimension());
        if (!targetSize.shouldResize()) {
            return;
        }

        BufferedImage resizedImage = resize(originalImage, targetSize);
        byte[] contents = encodeWebp(resizedImage);
        String filePath = buildVariantFilePath(originalFile.getFileId(), variantType);
        fileStorageService.storeBytesAs(contents, WEBP_MIME_TYPE, filePath);
        try {
            fileVariantRepository.save(FileVariant.builder()
                    .file(originalFile)
                    .variantType(variantType)
                    .filePath(filePath)
                    .fileSize((long) contents.length)
                    .mimeType(WEBP_MIME_TYPE)
                    .width(targetSize.width())
                    .height(targetSize.height())
                    .build());
        } catch (RuntimeException e) {
            fileStorageService.deleteFile(filePath);
            throw e;
        }
    }

    private boolean isResizableMimeType(String mimeType) {
        return JPEG_MIME_TYPE.equalsIgnoreCase(mimeType)
                || PNG_MIME_TYPE.equalsIgnoreCase(mimeType)
                || WEBP_MIME_TYPE.equalsIgnoreCase(mimeType);
    }

    private ImageSize resolveTargetSize(int width, int height, int maxDimension) {
        int longerSide = Math.max(width, height);
        if (longerSide <= maxDimension) {
            return new ImageSize(width, height, false);
        }
        double scale = (double) maxDimension / longerSide;
        return new ImageSize(
                Math.max(1, (int) Math.round(width * scale)),
                Math.max(1, (int) Math.round(height * scale)),
                true);
    }

    private BufferedImage resize(BufferedImage originalImage, ImageSize targetSize) {
        int imageType = originalImage.getColorModel().hasAlpha()
                ? BufferedImage.TYPE_INT_ARGB
                : BufferedImage.TYPE_INT_RGB;
        BufferedImage resizedImage = new BufferedImage(targetSize.width(), targetSize.height(), imageType);
        Graphics2D graphics = resizedImage.createGraphics();
        try {
            graphics.setRenderingHint(RenderingHints.KEY_INTERPOLATION, RenderingHints.VALUE_INTERPOLATION_BICUBIC);
            graphics.setRenderingHint(RenderingHints.KEY_RENDERING, RenderingHints.VALUE_RENDER_QUALITY);
            graphics.setRenderingHint(RenderingHints.KEY_ANTIALIASING, RenderingHints.VALUE_ANTIALIAS_ON);
            graphics.drawImage(
                    originalImage.getScaledInstance(targetSize.width(), targetSize.height(), Image.SCALE_SMOOTH),
                    0,
                    0,
                    null);
            return resizedImage;
        } finally {
            graphics.dispose();
        }
    }

    private byte[] encodeWebp(BufferedImage image) throws IOException {
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            if (!ImageIO.write(image, WEBP_FORMAT_NAME, outputStream)) {
                throw new IOException("No image writer for " + WEBP_FORMAT_NAME);
            }
            return outputStream.toByteArray();
        }
    }

    private String buildVariantFilePath(Long fileId, FileVariantType variantType) {
        return "variants/%d/%s.webp".formatted(
                fileId,
                variantType.getPathSegment().toLowerCase(Locale.ROOT));
    }

    private record ImageSize(int width, int height, boolean shouldResize) {
    }
}
