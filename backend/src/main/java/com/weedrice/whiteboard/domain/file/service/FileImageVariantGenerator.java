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
import org.springframework.dao.DataIntegrityViolationException;

import com.weedrice.whiteboard.domain.file.service.FileUploadValidationPolicy.ValidatedUpload;

import javax.imageio.ImageIO;
import javax.imageio.ImageReadParam;
import javax.imageio.ImageReader;
import javax.imageio.stream.ImageInputStream;
import java.awt.Graphics2D;
import java.awt.Image;
import java.awt.RenderingHints;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Locale;
import java.util.Iterator;
import java.nio.file.Files;
import java.nio.file.Path;

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
    private final FileVariantStateCommand stateCommand;
    private final FileUploadValidationPolicy validationPolicy;

    public void generateVariants(File originalFile, MultipartFile multipartFile, ValidatedUpload upload) {
        if (!isResizableMimeType(upload.detectedMimeType()) || originalFile.getFileId() == null) {
            return;
        }
        try {
            generateVariantsOrThrow(originalFile, multipartFile, upload);
        } catch (IOException | RuntimeException e) {
            log.warn("Failed to generate image variants. fileId={}", originalFile.getFileId(), e);
        }
    }

    public int generateMissingVariants(File originalFile) {
        MultipartFile storedFile = new StoredMultipartFile(originalFile, fileStorageService);
        try {
            return generateVariantsOrThrow(originalFile, storedFile, validationPolicy.validate(storedFile));
        } catch (IOException exception) {
            throw new IllegalStateException("Failed to regenerate image variants", exception);
        }
    }

    private int generateVariantsOrThrow(
            File originalFile, MultipartFile multipartFile, ValidatedUpload upload) throws IOException {
        int generated = 0;
        for (FileVariantType variantType : FileVariantType.values()) {
            if (generateVariant(originalFile, multipartFile, upload, variantType)) {
                generated++;
            }
        }
        return generated;
    }

    private boolean generateVariant(
            File originalFile,
            MultipartFile multipartFile,
            ValidatedUpload upload,
            FileVariantType variantType) throws IOException {
        if (fileVariantRepository.findByFileFileIdAndVariantType(originalFile.getFileId(), variantType).isPresent()) {
            return false;
        }
        ImageSize targetSize = resolveTargetSize(upload.width(), upload.height(), variantType.getMaxDimension());
        if (!targetSize.shouldResize()) {
            return false;
        }
        BufferedImage decodedImage = readSubsampled(multipartFile, upload, variantType.getMaxDimension());
        BufferedImage resizedImage = decodedImage.getWidth() == targetSize.width()
                && decodedImage.getHeight() == targetSize.height() ? decodedImage : resize(decodedImage, targetSize);
        byte[] contents = encodeWebp(resizedImage);
        String filePath = buildVariantFilePath(originalFile.getFileId(), variantType);
        FileVariant pendingVariant;
        try {
            pendingVariant = stateCommand.createPending(
                    originalFile, variantType, filePath, contents.length, WEBP_MIME_TYPE,
                    targetSize.width(), targetSize.height());
        } catch (DataIntegrityViolationException duplicate) {
            return false;
        }
        try {
            fileStorageService.storeBytesAs(contents, WEBP_MIME_TYPE, filePath);
            stateCommand.activate(pendingVariant.getFileVariantId());
            return true;
        } catch (RuntimeException e) {
            fileStorageService.deleteFile(filePath);
            throw e;
        }
    }

    private BufferedImage readSubsampled(
            MultipartFile multipartFile,
            ValidatedUpload upload,
            int maxDimension) throws IOException {
        try (InputStream inputStream = multipartFile.getInputStream();
                ImageInputStream imageInputStream = ImageIO.createImageInputStream(inputStream)) {
            if (imageInputStream == null) {
                throw new IOException("Unable to open image input stream");
            }
            Iterator<ImageReader> readers = ImageIO.getImageReaders(imageInputStream);
            if (!readers.hasNext()) {
                throw new IOException("No image reader available");
            }
            ImageReader reader = readers.next();
            try {
                reader.setInput(imageInputStream, true, true);
                int longerSide = Math.max(upload.width(), upload.height());
                int subsampling = Math.max(1, longerSide / maxDimension);
                ImageReadParam readParam = reader.getDefaultReadParam();
                readParam.setSourceSubsampling(subsampling, subsampling, 0, 0);
                BufferedImage image = reader.read(0, readParam);
                if (image == null) {
                    throw new IOException("Unable to decode image");
                }
                return image;
            } finally {
                reader.dispose();
            }
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

    private record StoredMultipartFile(File file, FileStorageService storageService) implements MultipartFile {
        @Override public String getName() { return "file"; }
        @Override public String getOriginalFilename() { return file.getOriginalName(); }
        @Override public String getContentType() { return file.getMimeType(); }
        @Override public boolean isEmpty() { return file.getFileSize() == null || file.getFileSize() == 0; }
        @Override public long getSize() { return file.getFileSize() == null ? 0 : file.getFileSize(); }
        @Override public byte[] getBytes() throws IOException {
            try (InputStream input = getInputStream()) { return input.readAllBytes(); }
        }
        @Override public InputStream getInputStream() { return storageService.loadFile(file.getFilePath()); }
        @Override public void transferTo(java.io.File destination) throws IOException {
            try (InputStream input = getInputStream()) { Files.copy(input, destination.toPath()); }
        }
        @Override public void transferTo(Path destination) throws IOException {
            try (InputStream input = getInputStream()) { Files.copy(input, destination); }
        }
    }
}
