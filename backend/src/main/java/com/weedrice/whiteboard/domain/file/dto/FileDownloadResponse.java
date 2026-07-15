package com.weedrice.whiteboard.domain.file.dto;

import java.io.InputStream;
import java.util.Objects;
import java.util.function.Supplier;

public record FileDownloadResponse(
        Supplier<InputStream> inputStreamSupplier,
        String originalName,
        String mimeType,
        boolean freshnessCacheable,
        String entityTag
) {

    public FileDownloadResponse {
        Objects.requireNonNull(inputStreamSupplier, "inputStreamSupplier");
    }

    public FileDownloadResponse(InputStream inputStream, String originalName, String mimeType) {
        this(() -> inputStream, originalName, mimeType, false, null);
    }

    public InputStream inputStream() {
        return inputStreamSupplier.get();
    }
}
