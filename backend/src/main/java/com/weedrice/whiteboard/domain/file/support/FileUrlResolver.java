package com.weedrice.whiteboard.domain.file.support;

import java.util.List;

public final class FileUrlResolver {

    private FileUrlResolver() {
    }

    public static String resolve(Long fileId) {
        return "/api/v1/files/" + fileId;
    }

    public static String resolveThumbnail(Long fileId) {
        return resolveVariant(fileId, "thumbnail");
    }

    public static String resolveMedium(Long fileId) {
        return resolveVariant(fileId, "medium");
    }

    public static String resolveVariant(Long fileId, String variantType) {
        return resolve(fileId) + "/variants/" + variantType;
    }

    public static List<String> referenceCandidates(Long fileId) {
        return List.of(resolve(fileId), "/files/" + fileId);
    }

    public static String normalize(String url) {
        if (url == null || url.isBlank()) {
            return url;
        }
        return url.replaceFirst("^/files/(\\d+)([?#].*)?$", "/api/v1/files/$1$2");
    }
}
