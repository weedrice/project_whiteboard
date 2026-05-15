package com.weedrice.whiteboard.domain.file.dto;

import java.io.InputStream;

public record FileDownloadResponse(
        InputStream inputStream,
        String originalName,
        String mimeType
) {
}
