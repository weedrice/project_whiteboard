package com.weedrice.whiteboard.global.security;

import java.net.URI;
import java.net.URISyntaxException;
import java.util.Locale;

final class FrontendOriginMatcher {

    private FrontendOriginMatcher() {
    }

    static boolean isAllowedOriginHeader(String frontendUrl, String origin) {
        URI frontendOrigin = parseOrigin(frontendUrl);
        URI requestOrigin = parseOrigin(origin);
        return frontendOrigin != null
                && requestOrigin != null
                && isOriginOnly(requestOrigin)
                && isSameOrigin(frontendOrigin, requestOrigin);
    }

    static boolean isAllowedReferer(String frontendUrl, String referer) {
        URI frontendOrigin = parseOrigin(frontendUrl);
        URI requestReferer = parseOrigin(referer);
        return frontendOrigin != null
                && requestReferer != null
                && isSameOrigin(frontendOrigin, requestReferer);
    }

    private static URI parseOrigin(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }

        try {
            URI uri = new URI(value.trim());
            if (uri.getScheme() == null || uri.getHost() == null) {
                return null;
            }
            return uri;
        } catch (URISyntaxException ex) {
            return null;
        }
    }

    private static boolean isSameOrigin(URI expected, URI actual) {
        return normalizeScheme(expected).equals(normalizeScheme(actual))
                && normalizeHost(expected).equals(normalizeHost(actual))
                && effectivePort(expected) == effectivePort(actual);
    }

    private static boolean isOriginOnly(URI uri) {
        String path = uri.getRawPath();
        return (path == null || path.isEmpty() || "/".equals(path))
                && uri.getRawQuery() == null
                && uri.getRawFragment() == null;
    }

    private static String normalizeScheme(URI uri) {
        return uri.getScheme().toLowerCase(Locale.ROOT);
    }

    private static String normalizeHost(URI uri) {
        return uri.getHost().toLowerCase(Locale.ROOT);
    }

    private static int effectivePort(URI uri) {
        if (uri.getPort() >= 0) {
            return uri.getPort();
        }
        return switch (normalizeScheme(uri)) {
            case "http" -> 80;
            case "https" -> 443;
            default -> -1;
        };
    }
}
