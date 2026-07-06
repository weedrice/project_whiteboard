package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;

final class FileDownloadResponseAssembler {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";

    private FileDownloadResponseAssembler() {
    }

    static ResponseEntity<Resource> toResponse(FileDownloadResponse download) {
        MediaType contentType = resolveContentType(download.mimeType());

        ContentDisposition contentDisposition = ContentDisposition.builder(resolveDisposition(contentType))
                .filename(sanitizeFileName(download.originalName()), StandardCharsets.UTF_8)
                .build();

        return ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff")
                .body(new InputStreamResource(download.inputStream()));
    }

    private static MediaType resolveContentType(String contentType) {
        if (contentType == null || contentType.isBlank()) {
            return MediaType.parseMediaType(DEFAULT_CONTENT_TYPE);
        }

        try {
            return MediaType.parseMediaType(contentType);
        } catch (InvalidMediaTypeException ex) {
            return MediaType.parseMediaType(DEFAULT_CONTENT_TYPE);
        }
    }

    private static String resolveDisposition(MediaType contentType) {
        if ("image".equalsIgnoreCase(contentType.getType())
                && !SVG_CONTENT_TYPE.equalsIgnoreCase(contentType.toString())) {
            return "inline";
        }
        return "attachment";
    }

    private static String sanitizeFileName(String originalName) {
        if (originalName == null || originalName.isBlank()) {
            return "file";
        }
        return originalName
                .replaceAll("[\\r\\n]+", "_")
                .replaceAll("[\\\\/]+", "_")
                .replace("\"", "_")
                .trim();
    }
}
