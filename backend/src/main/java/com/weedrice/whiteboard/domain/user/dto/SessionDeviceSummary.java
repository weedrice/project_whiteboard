package com.weedrice.whiteboard.domain.user.dto;

final class SessionDeviceSummary {

    private static final String UNKNOWN = "Unknown";

    private SessionDeviceSummary() {
    }

    static String from(String userAgent) {
        if (userAgent == null || userAgent.isBlank()) {
            return UNKNOWN;
        }
        return browser(userAgent) + " · " + os(userAgent);
    }

    private static String browser(String userAgent) {
        if (contains(userAgent, "Edg/")) {
            return "Edge";
        }
        if (contains(userAgent, "Chrome/") || contains(userAgent, "CriOS/")) {
            return "Chrome";
        }
        if (contains(userAgent, "Firefox/") || contains(userAgent, "FxiOS/")) {
            return "Firefox";
        }
        if (contains(userAgent, "Safari/")) {
            return "Safari";
        }
        return UNKNOWN;
    }

    private static String os(String userAgent) {
        if (contains(userAgent, "Windows")) {
            return "Windows";
        }
        if (contains(userAgent, "Mac OS X") || contains(userAgent, "Macintosh")) {
            return "macOS";
        }
        if (contains(userAgent, "Android")) {
            return "Android";
        }
        if (contains(userAgent, "iPhone") || contains(userAgent, "iPad")) {
            return "iOS";
        }
        if (contains(userAgent, "Linux")) {
            return "Linux";
        }
        return UNKNOWN;
    }

    private static boolean contains(String value, String token) {
        return value != null && value.contains(token);
    }
}
