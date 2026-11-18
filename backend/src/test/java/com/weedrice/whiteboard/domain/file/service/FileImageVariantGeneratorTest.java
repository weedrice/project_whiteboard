package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.domain.file.service.FileUploadValidationPolicy.ValidatedUpload;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullSource;
import org.junit.jupiter.params.provider.ValueSource;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.mock.web.MockMultipartFile;
import org.springframework.test.util.ReflectionTestUtils;
import software.amazon.awssdk.http.Abortable;

import javax.imageio.ImageIO;
import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
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

    @ParameterizedTest
    @NullSource
    @ValueSource(longs = {0L, 1L, 20_971_520L})
    void reconcileUsesActualObjectSizeAndSingleSnapshotForLegacyMetadata(Long recordedSize) throws Exception {
        File file = imageFile(10L);
        ReflectionTestUtils.setField(file, "fileSize", recordedSize);
        TrackingInputStream source = new TrackingInputStream(jpegFile("original.jpg", 1600, 800).getBytes());
        when(fileStorageService.loadFile("original.jpg")).thenReturn(source);
        when(stateCommand.createPending(any(), any(), any(), org.mockito.ArgumentMatchers.anyLong(), any(),
                org.mockito.ArgumentMatchers.anyInt(), org.mockito.ArgumentMatchers.anyInt()))
                .thenReturn(pendingVariant(1L), pendingVariant(2L));
        FileImageVariantGenerator generator = generator();

        FileImageVariantGenerator.ReconciliationResult result = generator.generateMissingVariants(file);

        assertThat(result.generated()).isEqualTo(2);
        assertThat(result.width()).isEqualTo(1600);
        assertThat(result.height()).isEqualTo(800);
        assertThat(result.expectedVariantCount()).isEqualTo(2);
        assertThat(file.getFileSize()).isEqualTo(recordedSize);
        assertThat(source.closed).isTrue();
        verify(fileStorageService).loadFile("original.jpg");
        verify(fileStorageService).storeBytesAs(any(), eq("image/webp"), eq("variants/10/thumbnail.webp"));
        verify(fileStorageService).storeBytesAs(any(), eq("image/webp"), eq("variants/10/medium.webp"));
        verify(stateCommand).activate(1L);
        verify(stateCommand).activate(2L);
    }

    @Test
    void reconcileRejectsOversizeObjectAfterBoundedReadAndClosesStream() {
        File file = imageFile(10L);
        ReflectionTestUtils.setField(file, "fileSize", 0L);
        UnlimitedInputStream source = new UnlimitedInputStream();
        when(fileStorageService.loadFile("original.jpg")).thenReturn(source);

        assertThatThrownBy(() -> generator().generateMissingVariants(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_TOO_LARGE));

        assertThat(source.bytesRead).isEqualTo(FileUploadValidationPolicy.MAX_FILE_SIZE + 1);
        assertThat(source.aborted).isTrue();
        assertThat(source.closed).isTrue();
        verify(fileStorageService, never()).storeBytesAs(any(), any(), any());
    }

    @Test
    void reconcileStillRejectsEmptyObjectRegardlessOfPositiveMetadata() {
        TrackingInputStream source = new TrackingInputStream(new byte[0]);
        when(fileStorageService.loadFile("original.jpg")).thenReturn(source);

        assertThatThrownBy(() -> generator().generateMissingVariants(imageFile(10L)))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.FILE_EMPTY));

        assertThat(source.closed).isTrue();
        verify(fileStorageService, never()).storeBytesAs(any(), any(), any());
    }

    @Test
    void reconcileStillValidatesContentTypeWithZeroMetadata() throws Exception {
        File file = imageFile(10L);
        ReflectionTestUtils.setField(file, "fileSize", 0L);
        ReflectionTestUtils.setField(file, "mimeType", "image/png");
        TrackingInputStream source = new TrackingInputStream(jpegFile("original.jpg", 100, 80).getBytes());
        when(fileStorageService.loadFile("original.jpg")).thenReturn(source);

        assertThatThrownBy(() -> generator().generateMissingVariants(file))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertThat(exception.getErrorCode()).isEqualTo(ErrorCode.INVALID_FILE_TYPE));

        assertThat(source.closed).isTrue();
        verify(fileStorageService, never()).storeBytesAs(any(), any(), any());
    }

    @Test
    void reconcileReadFailureAbortsAndClosesStreamWithoutWritingVariants() throws Exception {
        InputStream source = org.mockito.Mockito.mock(InputStream.class,
                org.mockito.Mockito.withSettings().extraInterfaces(Abortable.class));
        when(source.readNBytes(org.mockito.ArgumentMatchers.anyInt())).thenThrow(new IOException("read failed"));
        when(fileStorageService.loadFile("original.jpg")).thenReturn(source);

        assertThatThrownBy(() -> generator().generateMissingVariants(imageFile(10L)))
                .isInstanceOf(IllegalStateException.class).hasCauseInstanceOf(IOException.class);

        verify((Abortable) source).abort();
        verify(source).close();
        verify(fileStorageService, never()).storeBytesAs(any(), any(), any());
    }

    private FileImageVariantGenerator generator() {
        return new FileImageVariantGenerator(
                fileStorageService, fileVariantRepository, stateCommand, new FileUploadValidationPolicy());
    }

    private static class TrackingInputStream extends ByteArrayInputStream {
        private boolean closed;

        TrackingInputStream(byte[] contents) { super(contents); }
        @Override public void close() throws IOException { closed = true; super.close(); }
    }

    private static class UnlimitedInputStream extends InputStream implements Abortable {
        private long bytesRead;
        private boolean closed;
        private boolean aborted;

        @Override public int read() { bytesRead++; return 1; }
        @Override public int read(byte[] buffer, int offset, int length) {
            Arrays.fill(buffer, offset, offset + length, (byte) 1);
            bytesRead += length;
            return length;
        }
        @Override public void abort() { aborted = true; }
        @Override public void close() { closed = true; }
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
