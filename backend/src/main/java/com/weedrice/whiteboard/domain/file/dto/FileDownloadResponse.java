package com.weedrice.whiteboard.domain.file.dto;

import org.springframework.core.io.Resource;
import org.springframework.http.ContentDisposition;
import org.springframework.http.MediaType;

public record FileDownloadResponse(
        Resource resource,
        MediaType contentType,
        ContentDisposition contentDisposition
) {
}
