package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.domain.file.entity.FileVariant;
import com.weedrice.whiteboard.domain.file.entity.FileVariantType;
import com.weedrice.whiteboard.domain.file.repository.FileVariantRepository;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDownloadServiceTest {

    @Mock
    private FileAccessService fileAccessService;

    @Mock
    private FileStorageService fileStorageService;
    @Mock
    private FileVariantRepository fileVariantRepository;

    @InjectMocks
    private FileDownloadService fileDownloadService;

    @Test
    @DisplayName("다운로드 데이터는 원본 메타데이터와 스트림을 반환한다")
    void downloadFile_returnsFileMetadataAndStream() {
        File file = file("document", null, "path/to/document");
        ByteArrayInputStream inputStream = new ByteArrayInputStream("content".getBytes());
        when(fileAccessService.getFileForDownload(1L, null)).thenReturn(file);
        when(fileStorageService.loadFile("path/to/document")).thenReturn(inputStream);

        FileDownloadResponse response = fileDownloadService.downloadFile(1L, null);

        assertThat(response.inputStream()).isSameAs(inputStream);
        assertThat(response.originalName()).isEqualTo("document");
        assertThat(response.mimeType()).isNull();
        verify(fileAccessService).getFileForDownload(1L, null);
    }

    @Test
    @DisplayName("인증 다운로드는 조회자 ID를 접근 검증에 전달한다")
    void downloadFile_passesViewerUserIdToAccessService() {
        File file = file("image.png", "image/png", "path/to/image.png");
        when(fileAccessService.getFileForDownload(2L, 10L)).thenReturn(file);
        when(fileStorageService.loadFile("path/to/image.png")).thenReturn(new ByteArrayInputStream("content".getBytes()));

        FileDownloadResponse response = fileDownloadService.downloadFile(2L, 10L);

        assertThat(response.mimeType()).isEqualTo("image/png");
        verify(fileAccessService).getFileForDownload(2L, 10L);
    }

    @Test
    void downloadVariantFile_returnsVariantWhenGenerated() {
        File file = file("image.png", "image/png", "path/to/image.png");
        FileVariant variant = FileVariant.builder()
                .file(file)
                .variantType(FileVariantType.THUMBNAIL)
                .filePath("variants/2/thumbnail.png")
                .fileSize(10L)
                .mimeType("image/png")
                .width(320)
                .height(240)
                .build();
        ByteArrayInputStream inputStream = new ByteArrayInputStream("variant".getBytes());
        when(fileAccessService.getFileForDownload(2L, 10L)).thenReturn(file);
        when(fileVariantRepository.findByFileFileIdAndVariantType(2L, FileVariantType.THUMBNAIL))
                .thenReturn(Optional.of(variant));
        when(fileStorageService.loadFile("variants/2/thumbnail.png")).thenReturn(inputStream);

        FileDownloadResponse response = fileDownloadService.downloadVariantFile(2L, FileVariantType.THUMBNAIL, 10L);

        assertThat(response.inputStream()).isSameAs(inputStream);
        assertThat(response.originalName()).isEqualTo("image.png");
        assertThat(response.mimeType()).isEqualTo("image/png");
    }

    @Test
    void downloadVariantFile_fallsBackToOriginalWhenVariantMissing() {
        File file = file("image.png", "image/png", "path/to/image.png");
        ByteArrayInputStream inputStream = new ByteArrayInputStream("original".getBytes());
        when(fileAccessService.getFileForDownload(2L, null)).thenReturn(file);
        when(fileVariantRepository.findByFileFileIdAndVariantType(2L, FileVariantType.MEDIUM))
                .thenReturn(Optional.empty());
        when(fileStorageService.loadFile("path/to/image.png")).thenReturn(inputStream);

        FileDownloadResponse response = fileDownloadService.downloadVariantFile(2L, FileVariantType.MEDIUM, null);

        assertThat(response.inputStream()).isSameAs(inputStream);
        assertThat(response.originalName()).isEqualTo("image.png");
        assertThat(response.mimeType()).isEqualTo("image/png");
    }

    private File file(String originalName, String mimeType, String filePath) {
        File file = File.builder().build();
        ReflectionTestUtils.setField(file, "originalName", originalName);
        ReflectionTestUtils.setField(file, "mimeType", mimeType);
        ReflectionTestUtils.setField(file, "filePath", filePath);
        return file;
    }
}
