package com.weedrice.whiteboard.domain.post.support;

import com.weedrice.whiteboard.domain.file.service.FileService;

import java.net.URI;

/** Identifies local attachments without treating an external URL as an owned file. */
public final class PostContentFileReferences {
    private PostContentFileReferences() {
    }

    public static Long extractFileId(String source, URI frontendOrigin) {
        Long relativeFileId = FileService.extractInternalContentFileIdFromUrl(source);
        if (relativeFileId != null) return relativeFileId;
        if (source == null || source.isBlank()) return null;
        try {
            URI uri = frontendOrigin.resolve(source.trim());
            if (uri.getHost() == null || uri.getUserInfo() != null
                    || !hasSameOrigin(uri, frontendOrigin)) return null;
            return FileService.extractInternalContentFileIdFromUrl(uri.getRawPath());
        } catch (IllegalArgumentException ex) {
            return null;
        }
    }

    private static boolean hasSameOrigin(URI candidate, URI expected) {
        return candidate.getScheme() != null && expected.getHost() != null
                && candidate.getScheme().equalsIgnoreCase(expected.getScheme())
                && candidate.getHost().equalsIgnoreCase(expected.getHost())
                && normalizePort(candidate) == normalizePort(expected);
    }

    private static int normalizePort(URI uri) {
        if (uri.getPort() >= 0) return uri.getPort();
        return "https".equalsIgnoreCase(uri.getScheme()) ? 443 : 80;
    }
}
