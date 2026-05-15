package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;

import java.io.ByteArrayInputStream;

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

    private FileDownloadResponse download(String originalName, String mimeType) {
        return new FileDownloadResponse(
                new ByteArrayInputStream("content".getBytes()),
                originalName,
                mimeType);
    }
}
