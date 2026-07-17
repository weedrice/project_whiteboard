package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.domain.file.service.FileUploadValidationPolicy.ValidatedUpload;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileImageVariantGeneratorTest {

    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private FileVariantRepository fileVariantRepository;
    @Mock
    private FileVariantStateCommand stateCommand;

    @Test
    void expectedVariantCountMatchesSmallMediumAndLargeContracts() {
        assertThat(FileImageVariantGenerator.expectedVariantCount(100, 80)).isZero();
        assertThat(FileImageVariantGenerator.expectedVariantCount(800, 600)).isEqualTo(1);
        assertThat(FileImageVariantGenerator.expectedVariantCount(2000, 1500)).isEqualTo(2);
    }

    @Test
    void generateVariants_resizesJpegIntoThumbnailAndMedium() throws Exception {
        File file = imageFile(10L);
        MockMultipartFile multipartFile = jpegFile("large.jpg", 1600, 800);
        when(stateCommand.createPending(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(pendingVariant(1L), pendingVariant(2L));
        FileImageVariantGenerator generator = new FileImageVariantGenerator(
                fileStorageService, fileVariantRepository, stateCommand, new FileUploadValidationPolicy());

        generator.generateVariants(file, multipartFile, validated("image/jpeg", 1600, 800));

        ArgumentCaptor<byte[]> contentsCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).storeBytesAs(contentsCaptor.capture(), eq("image/webp"), eq("variants/10/thumbnail.webp"));
        verify(fileStorageService).storeBytesAs(any(byte[].class), eq("image/webp"), eq("variants/10/medium.webp"));

        verify(stateCommand).createPending(
                file, FileVariantType.THUMBNAIL, "variants/10/thumbnail.webp",
                contentsCaptor.getValue().length, "image/webp", 320, 160);
        verify(stateCommand).createPending(
                eq(file), eq(FileVariantType.MEDIUM), eq("variants/10/medium.webp"),
                org.mockito.ArgumentMatchers.anyLong(), eq("image/webp"), eq(1280), eq(640));
        verify(stateCommand).activate(1L);
        verify(stateCommand).activate(2L);
        assertThat(contentsCaptor.getValue()).isNotEmpty();
    }

    @Test
    void generateVariants_skipsUnsupportedOrAlreadySmallImages() throws Exception {
        File file = imageFile(11L);
        MockMultipartFile multipartFile = jpegFile("small.jpg", 100, 80);
        FileImageVariantGenerator generator = new FileImageVariantGenerator(
                fileStorageService, fileVariantRepository, stateCommand, new FileUploadValidationPolicy());

        generator.generateVariants(file, multipartFile, validated("image/jpeg", 100, 80));
        generator.generateVariants(file, multipartFile, validated("image/gif", 100, 80));

        verify(fileStorageService, never()).storeBytesAs(any(), any(), any());
        verify(stateCommand, never()).createPending(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt());
    }

    private FileVariant pendingVariant(Long id) {
        FileVariant variant = FileVariant.builder()
                .file(imageFile(99L))
                .variantType(FileVariantType.THUMBNAIL)
                .filePath("pending.webp")
                .fileSize(1L)
                .mimeType("image/webp")
                .width(1)
                .height(1)
                .storageStatus(com.weedrice.whiteboard.domain.file.entity.FileStorageStatus.PENDING_UPLOAD)
                .build();
        ReflectionTestUtils.setField(variant, "fileVariantId", id);
        return variant;
    }

    private File imageFile(Long fileId) {
        File file = File.builder()
                .filePath("original.jpg")
                .originalName("original.jpg")
                .fileSize(4L)
                .mimeType("image/jpeg")
                .uploader(User.builder().build())
                .build();
        ReflectionTestUtils.setField(file, "fileId", fileId);
        return file;
    }

    private MockMultipartFile jpegFile(String filename, int width, int height) throws IOException {
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D graphics = image.createGraphics();
        try {
            graphics.setColor(Color.BLUE);
            graphics.fillRect(0, 0, width, height);
        } finally {
            graphics.dispose();
        }
        try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
            ImageIO.write(image, "jpg", outputStream);
            return new MockMultipartFile("file", filename, "image/jpeg", outputStream.toByteArray());
        }
    }

    private ValidatedUpload validated(String mimeType, int width, int height) {
        return new ValidatedUpload("test.jpg", mimeType, 1L, "JPEG", width, height);
    }
}
