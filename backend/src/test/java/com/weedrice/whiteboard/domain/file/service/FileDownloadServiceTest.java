package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.io.ByteArrayInputStream;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class FileDownloadServiceTest {

    @Mock
    private FileService fileService;

    @Mock
    private FileStorageService fileStorageService;

    @InjectMocks
    private FileDownloadService fileDownloadService;

    @Test
    @DisplayName("다운로드 응답은 MIME이 없으면 기본 content type과 attachment를 사용한다")
    void downloadFile_usesDefaultContentTypeAndAttachment() {
        File file = file("document", null, "path/to/document");
        when(fileService.getFileForDownload(1L, null)).thenReturn(file);
        when(fileStorageService.loadFile("path/to/document")).thenReturn(new ByteArrayInputStream("content".getBytes()));

        FileDownloadResponse response = fileDownloadService.downloadFile(1L, null);

        assertThat(response.contentType().toString()).isEqualTo("application/octet-stream");
        assertThat(response.contentDisposition().getType()).isEqualTo("attachment");
        verify(fileService).getFileForDownload(1L, null);
    }

    @Test
    @DisplayName("이미지 파일은 SVG가 아니면 inline으로 응답한다")
    void downloadFile_servesImageInline() {
        File file = file("image.png", "image/png", "path/to/image.png");
        when(fileService.getFileForDownload(2L, 10L)).thenReturn(file);
        when(fileStorageService.loadFile("path/to/image.png")).thenReturn(new ByteArrayInputStream("content".getBytes()));

        FileDownloadResponse response = fileDownloadService.downloadFile(2L, 10L);

        assertThat(response.contentType().toString()).isEqualTo("image/png");
        assertThat(response.contentDisposition().getType()).isEqualTo("inline");
        verify(fileService).getFileForDownload(2L, 10L);
    }

    @Test
    @DisplayName("SVG 파일은 attachment로 응답한다")
    void downloadFile_servesSvgAsAttachment() {
        File file = file("vector.svg", "image/svg+xml", "path/to/vector.svg");
        when(fileService.getFileForDownload(3L, null)).thenReturn(file);
        when(fileStorageService.loadFile("path/to/vector.svg")).thenReturn(new ByteArrayInputStream("content".getBytes()));

        FileDownloadResponse response = fileDownloadService.downloadFile(3L, null);

        assertThat(response.contentDisposition().getType()).isEqualTo("attachment");
    }

    @Test
    @DisplayName("Content-Disposition 파일명은 헤더 주입 문자를 제거한다")
    void downloadFile_sanitizesFileName() {
        File file = file("bad/name\r\n.txt", "text/plain", "path/to/file");
        when(fileService.getFileForDownload(eq(4L), isNull())).thenReturn(file);
        when(fileStorageService.loadFile("path/to/file")).thenReturn(new ByteArrayInputStream("content".getBytes()));

        FileDownloadResponse response = fileDownloadService.downloadFile(4L, null);

        assertThat(response.contentDisposition().getFilename()).isEqualTo("bad_name_.txt");
    }

    private File file(String originalName, String mimeType, String filePath) {
        File file = File.builder().build();
        ReflectionTestUtils.setField(file, "originalName", originalName);
        ReflectionTestUtils.setField(file, "mimeType", mimeType);
        ReflectionTestUtils.setField(file, "filePath", filePath);
        return file;
    }
}
