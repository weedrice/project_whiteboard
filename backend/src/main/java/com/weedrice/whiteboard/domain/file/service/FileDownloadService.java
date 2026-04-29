package com.weedrice.whiteboard.domain.file.service;

import com.weedrice.whiteboard.domain.file.dto.FileDownloadResponse;
import com.weedrice.whiteboard.domain.file.entity.File;
import com.weedrice.whiteboard.global.common.util.FileStorageService;
import lombok.RequiredArgsConstructor;
import org.springframework.core.io.InputStreamResource;
import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;
import org.springframework.stereotype.Service;

import java.io.InputStream;
import java.nio.charset.StandardCharsets;

@Service
@RequiredArgsConstructor
public class FileDownloadService {

    private static final String DEFAULT_CONTENT_TYPE = "application/octet-stream";
    private static final String SVG_CONTENT_TYPE = "image/svg+xml";

    private final FileService fileService;
    private final FileStorageService fileStorageService;

    public FileDownloadResponse downloadFile(Long fileId, Long viewerUserId) {
        File file = fileService.getFileForDownload(fileId, viewerUserId);
        InputStream inputStream = fileStorageService.loadFile(file.getFilePath());
        Resource resource = new InputStreamResource(inputStream);

        String contentTypeValue = file.getMimeType();
        if (contentTypeValue == null) {
            contentTypeValue = DEFAULT_CONTENT_TYPE;
        }

        String disposition = isInlinePreview(contentTypeValue) ? "inline" : "attachment";
        ContentDisposition contentDisposition = ContentDisposition.builder(disposition)
                .filename(sanitizeFileName(file.getOriginalName()), StandardCharsets.UTF_8)
                .build();

        return new FileDownloadResponse(
                resource,
                MediaType.parseMediaType(contentTypeValue),
                contentDisposition);
    }

    private boolean isInlinePreview(String contentType) {
        return contentType.startsWith("image/")
                && !SVG_CONTENT_TYPE.equalsIgnoreCase(contentType);
    }

    private String sanitizeFileName(String originalName) {
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
