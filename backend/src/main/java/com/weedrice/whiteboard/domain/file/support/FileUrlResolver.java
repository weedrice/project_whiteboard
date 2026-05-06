package com.weedrice.whiteboard.domain.file.support;

public final class FileUrlResolver {

    private FileUrlResolver() {
    }

    public static String resolve(Long fileId) {
        return "/api/v1/files/" + fileId;
    }

    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        return url.replaceFirst("^/files/(\\d+)([?#].*)?$", "/api/v1/files/$1$2");
    }
}
