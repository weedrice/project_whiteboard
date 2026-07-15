package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
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

    @Test
    void generateVariants_resizesJpegIntoThumbnailAndMedium() throws Exception {
        File file = imageFile(10L);
        MockMultipartFile multipartFile = jpegFile("large.jpg", 1600, 800);
        when(fileVariantRepository.save(any(FileVariant.class))).thenAnswer(invocation -> invocation.getArgument(0));
        FileImageVariantGenerator generator = new FileImageVariantGenerator(fileStorageService, fileVariantRepository);

        generator.generateVariants(file, multipartFile, "image/jpeg");

        ArgumentCaptor<byte[]> contentsCaptor = ArgumentCaptor.forClass(byte[].class);
        verify(fileStorageService).storeBytesAs(contentsCaptor.capture(), eq("image/webp"), eq("variants/10/thumbnail.webp"));
        verify(fileStorageService).storeBytesAs(any(byte[].class), eq("image/webp"), eq("variants/10/medium.webp"));

        ArgumentCaptor<FileVariant> variantCaptor = ArgumentCaptor.forClass(FileVariant.class);
        verify(fileVariantRepository, org.mockito.Mockito.times(2)).save(variantCaptor.capture());
        List<FileVariant> variants = variantCaptor.getAllValues();
        assertThat(variants)
                .extracting(FileVariant::getVariantType)
                .containsExactly(FileVariantType.THUMBNAIL, FileVariantType.MEDIUM);
        assertThat(variants.get(0).getWidth()).isEqualTo(320);
        assertThat(variants.get(0).getHeight()).isEqualTo(160);
        assertThat(variants.get(1).getWidth()).isEqualTo(1280);
        assertThat(variants.get(1).getHeight()).isEqualTo(640);
        assertThat(contentsCaptor.getValue()).isNotEmpty();
    }

    @Test
    void generateVariants_skipsUnsupportedOrAlreadySmallImages() throws Exception {
        File file = imageFile(11L);
        MockMultipartFile multipartFile = jpegFile("small.jpg", 100, 80);
        FileImageVariantGenerator generator = new FileImageVariantGenerator(fileStorageService, fileVariantRepository);

        generator.generateVariants(file, multipartFile, "image/jpeg");
        generator.generateVariants(file, multipartFile, "image/gif");

        verify(fileStorageService, never()).storeBytesAs(any(), any(), any());
        verify(fileVariantRepository, never()).save(any());
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
}
