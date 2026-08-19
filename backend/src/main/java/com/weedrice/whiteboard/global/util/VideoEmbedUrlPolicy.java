package com.weedrice.whiteboard.global.util;

import java.net.URI;
import java.util.Locale;

public final class VideoEmbedUrlPolicy {

    private VideoEmbedUrlPolicy() {
    }

    public static boolean isAllowed(String source) {
        if (source == null || source.isBlank()) {
            return false;
        }
        try {
            URI uri = URI.create(source);
            String host = uri.getHost();
            String path = uri.getPath();
            if (!"https".equalsIgnoreCase(uri.getScheme())
                    || host == null
                    || path == null
                    || uri.getUserInfo() != null
                    || normalizePort(uri) != 443) {
                return false;
            }
            String normalizedHost = host.toLowerCase(Locale.ROOT);
            return (("www.youtube.com".equals(normalizedHost)
                    || "youtube.com".equals(normalizedHost)
                    || "www.youtube-nocookie.com".equals(normalizedHost)
                    || "youtube-nocookie.com".equals(normalizedHost))
                    && path.matches("^/embed/[^/]+$"))
                    || "player.vimeo.com".equals(normalizedHost) && path.matches("^/video/\\d+/?$");
        } catch (IllegalArgumentException ex) {
            return false;
        }
    }

    private static int normalizePort(URI uri) {
        return uri.getPort() >= 0 ? uri.getPort() : 443;
    }
}
