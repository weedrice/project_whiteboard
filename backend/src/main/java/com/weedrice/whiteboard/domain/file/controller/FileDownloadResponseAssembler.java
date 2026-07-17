package com.weedrice.whiteboard.domain.file.controller;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.InvalidMediaTypeException;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;

import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.util.Arrays;

final class FileDownloadResponseAssembler {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";
    private static final Duration EMOTICON_FRESHNESS = Duration.ofMinutes(10);

    private FileDownloadResponseAssembler() {
    }

    static ResponseEntity<Resource> toResponse(FileDownloadResponse download) {
        return toResponse(download, null);
    }

    static ResponseEntity<Resource> toResponse(FileDownloadResponse download, String ifNoneMatch) {
        if (isNotModified(download, ifNoneMatch)) {
            return applyCacheHeaders(ResponseEntity.status(HttpStatus.NOT_MODIFIED), download)
                    .build();
        }

        MediaType contentType = resolveContentType(download.mimeType());

        ContentDisposition contentDisposition = ContentDisposition.builder(resolveDisposition(contentType))
                .filename(sanitizeFileName(download.originalName()), StandardCharsets.UTF_8)
                .build();

        ResponseEntity.BodyBuilder responseBuilder = ResponseEntity.ok()
                .contentType(contentType)
                .header(HttpHeaders.CONTENT_DISPOSITION, contentDisposition.toString())
                .header("X-Content-Type-Options", "nosniff");

        return applyCacheHeaders(responseBuilder, download)
                .body(new InputStreamResource(download.inputStream()));
    }

    private static ResponseEntity.BodyBuilder applyCacheHeaders(
            ResponseEntity.BodyBuilder builder,
            FileDownloadResponse download) {
        if (!download.freshnessCacheable() || download.entityTag() == null) {
            return builder
                    .cacheControl(org.springframework.http.CacheControl.noStore().cachePrivate())
                    .varyBy(HttpHeaders.AUTHORIZATION, HttpHeaders.COOKIE);
        }
        return builder
                .cacheControl(org.springframework.http.CacheControl.maxAge(EMOTICON_FRESHNESS).cachePrivate())
                .eTag(download.entityTag());
    }

    private static boolean isNotModified(FileDownloadResponse download, String ifNoneMatch) {
        if (!download.freshnessCacheable()
                || download.entityTag() == null
                || ifNoneMatch == null
                || ifNoneMatch.isBlank()) {
            return false;
        }
        String quotedEntityTag = '"' + download.entityTag() + '"';
        return Arrays.stream(ifNoneMatch.split(","))
                .map(String::trim)
                .anyMatch(candidate -> "*".equals(candidate)
                        || quotedEntityTag.equals(candidate)
                        || ("W/" + quotedEntityTag).equals(candidate));
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
