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
import static org.mockito.Mockito.verifyNoInteractions;
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
        when(fileAccessService.getFileAccessForDownload(1L, null)).thenReturn(access(file));
        when(fileStorageService.loadFile("path/to/document")).thenReturn(inputStream);

        FileDownloadResponse response = fileDownloadService.downloadFile(1L, null);

        verifyNoInteractions(fileStorageService);
        assertThat(response.inputStream()).isSameAs(inputStream);
        assertThat(response.originalName()).isEqualTo("document");
        assertThat(response.mimeType()).isNull();
        verify(fileAccessService).getFileAccessForDownload(1L, null);
    }

    @Test
    @DisplayName("인증 다운로드는 조회자 ID를 접근 검증에 전달한다")
    void downloadFile_passesViewerUserIdToAccessService() {
        File file = file("image.png", "image/png", "path/to/image.png");
        when(fileAccessService.getFileAccessForDownload(2L, 10L)).thenReturn(access(file));

        FileDownloadResponse response = fileDownloadService.downloadFile(2L, 10L);

        assertThat(response.mimeType()).isEqualTo("image/png");
        verify(fileAccessService).getFileAccessForDownload(2L, 10L);
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
        when(fileAccessService.getFileAccessForDownload(2L, 10L)).thenReturn(access(file));
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
        when(fileAccessService.getFileAccessForDownload(2L, null)).thenReturn(access(file));
        when(fileVariantRepository.findByFileFileIdAndVariantType(2L, FileVariantType.MEDIUM))
                .thenReturn(Optional.empty());
        when(fileStorageService.loadFile("path/to/image.png")).thenReturn(inputStream);

        FileDownloadResponse response = fileDownloadService.downloadVariantFile(2L, FileVariantType.MEDIUM, null);

        assertThat(response.inputStream()).isSameAs(inputStream);
        assertThat(response.originalName()).isEqualTo("image.png");
        assertThat(response.mimeType()).isEqualTo("image/png");
    }

    @Test
    void downloadFile_publicEmoticonCreatesLazyCacheableResponse() {
        File file = file("emoticon.png", "image/png", "emoticons/original.png");
        ReflectionTestUtils.setField(file, "fileSize", 20L);
        when(fileAccessService.getFileAccessForDownload(9L, null))
                .thenReturn(new FileAccessResult(file, FileAccessResult.CacheScope.PUBLIC_EMOTICON));
        when(fileStorageService.loadFile("emoticons/original.png"))
                .thenReturn(new ByteArrayInputStream("content".getBytes()));

        FileDownloadResponse response = fileDownloadService.downloadFile(9L, null);

        assertThat(response.freshnessCacheable()).isTrue();
        assertThat(response.entityTag()).hasSize(64);
        verifyNoInteractions(fileStorageService);
        response.inputStream();
        verify(fileStorageService).loadFile("emoticons/original.png");
    }

    @Test
    void downloadVariantFile_publicEmoticonUsesVariantMetadataForEntityTag() {
        File file = file("emoticon.png", "image/png", "emoticons/original.png");
        ReflectionTestUtils.setField(file, "fileSize", 20L);
        FileVariant variant = FileVariant.builder()
                .file(file)
                .variantType(FileVariantType.THUMBNAIL)
                .filePath("emoticons/thumbnail.webp")
                .fileSize(10L)
                .mimeType("image/webp")
                .width(320)
                .height(240)
                .build();
        when(fileAccessService.getFileAccessForDownload(9L, null))
                .thenReturn(new FileAccessResult(file, FileAccessResult.CacheScope.PUBLIC_EMOTICON));
        when(fileVariantRepository.findByFileFileIdAndVariantType(9L, FileVariantType.THUMBNAIL))
                .thenReturn(Optional.of(variant));

        FileDownloadResponse original = fileDownloadService.downloadFile(9L, null);
        FileDownloadResponse thumbnail = fileDownloadService.downloadVariantFile(9L, FileVariantType.THUMBNAIL, null);

        assertThat(thumbnail.freshnessCacheable()).isTrue();
        assertThat(thumbnail.entityTag()).isNotEqualTo(original.entityTag());
        assertThat(thumbnail.mimeType()).isEqualTo("image/webp");
        verifyNoInteractions(fileStorageService);
    }

    @Test
    void downloadFile_restrictedEmoticonDoesNotEnableFreshnessCache() {
        File file = file("emoticon.png", "image/png", "emoticons/restricted.png");
        when(fileAccessService.getFileAccessForDownload(9L, 1L))
                .thenReturn(new FileAccessResult(file, FileAccessResult.CacheScope.RESTRICTED_EMOTICON));

        FileDownloadResponse response = fileDownloadService.downloadFile(9L, 1L);

        assertThat(response.freshnessCacheable()).isFalse();
        assertThat(response.entityTag()).isNull();
        verifyNoInteractions(fileStorageService);
    }

    private File file(String originalName, String mimeType, String filePath) {
        File file = File.builder().build();
        ReflectionTestUtils.setField(file, "originalName", originalName);
        ReflectionTestUtils.setField(file, "mimeType", mimeType);
        ReflectionTestUtils.setField(file, "filePath", filePath);
        return file;
    }

    private FileAccessResult access(File file) {
        return new FileAccessResult(file, FileAccessResult.CacheScope.OTHER);
    }
}
