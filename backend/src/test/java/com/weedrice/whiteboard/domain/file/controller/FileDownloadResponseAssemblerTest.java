package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

class FileDownloadResponseAssemblerTest {

    @Test
    @DisplayName("MIME이 없으면 기본 content type과 attachment를 사용한다")
    void toResponse_usesDefaultContentTypeAndAttachment() {
        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(download("document", null));

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
        assertThat(response.getHeaders().getFirst("X-Content-Type-Options")).isEqualTo("nosniff");
    }

    @Test
    void toResponse_fallsBackToOctetStreamForInvalidMimeType() {
        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(download("document", "bad mime"));

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("application/octet-stream");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
    }

    @Test
    @DisplayName("이미지 파일은 SVG가 아니면 inline으로 응답한다")
    void toResponse_servesImageInline() {
        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(download("image.png", "image/png"));

        assertThat(response.getHeaders().getContentType().toString()).isEqualTo("image/png");
        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("inline");
    }

    @Test
    @DisplayName("SVG 파일은 attachment로 응답한다")
    void toResponse_servesSvgAsAttachment() {
        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(download("vector.svg", "image/svg+xml"));

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("attachment");
    }

    @Test
    @DisplayName("Content-Disposition 파일명은 헤더 주입 문자를 제거한다")
    void toResponse_sanitizesFileName() {
        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(download("bad/name\r\n.txt", "text/plain"));

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION)).contains("bad_name_.txt");
    }

    @Test
    void toResponse_matchesSharedUtf8ContentDispositionContract() throws IOException {
        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(
                download("보고서.pdf", "application/pdf"));

        String expectedHeader;
        try (var input = getClass().getResourceAsStream(
                "/contracts/file-download-content-disposition-utf8.txt")) {
            assertThat(input).isNotNull();
            expectedHeader = new String(input.readAllBytes(), StandardCharsets.UTF_8).trim();
        }

        assertThat(response.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION))
                .isEqualTo(expectedHeader);
    }

    @Test
    void toResponse_cacheableEmoticonAddsPrivateFreshnessAndEntityTag() {
        FileDownloadResponse download = cacheableDownload(new AtomicInteger());

        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(download, null);

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("max-age=600, private");
        assertThat(response.getHeaders().getETag()).isEqualTo("\"etag-value\"");
    }

    @Test
    void toResponse_matchingEntityTagReturns304WithoutOpeningStorageStream() {
        AtomicInteger streamOpenCount = new AtomicInteger();
        FileDownloadResponse download = cacheableDownload(streamOpenCount);

        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(
                download,
                "W/\"etag-value\"");

        assertThat(response.getStatusCode().value()).isEqualTo(304);
        assertThat(response.getBody()).isNull();
        assertThat(response.getHeaders().getCacheControl()).isEqualTo("max-age=600, private");
        assertThat(response.getHeaders().getETag()).isEqualTo("\"etag-value\"");
        assertThat(streamOpenCount).hasValue(0);
    }

    @Test
    void toResponse_nonCacheableFileIgnoresMatchingEntityTag() {
        ResponseEntity<Resource> response = FileDownloadResponseAssembler.toResponse(
                download("document", "text/plain"),
                "*");

        assertThat(response.getStatusCode().value()).isEqualTo(200);
        assertThat(response.getHeaders().getCacheControl()).contains("no-store").contains("private");
        assertThat(response.getHeaders().getVary()).containsExactly("Authorization", "Cookie");
        assertThat(response.getHeaders().getETag()).isNull();
    }

    private FileDownloadResponse download(String originalName, String mimeType) {
        return new FileDownloadResponse(
                new ByteArrayInputStream("content".getBytes()),
                originalName,
                mimeType);
    }

    private FileDownloadResponse cacheableDownload(AtomicInteger streamOpenCount) {
        return new FileDownloadResponse(
                () -> {
                    streamOpenCount.incrementAndGet();
                    return new ByteArrayInputStream("content".getBytes());
                },
                "emoticon.png",
                "image/png",
                true,
                "etag-value");
    }
}
