package com.weedrice.whiteboard.domain.user.dto;

final class SessionIpMasker {

    private SessionIpMasker() {
    }

    static String mask(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank() || "unknown".equalsIgnoreCase(ipAddress)) {
            return "unknown";
        }
        String normalized = ipAddress.trim();
        if (normalized.contains(".")) {
            String[] parts = normalized.split("\\.");
            if (parts.length == 4) {
                return parts[0] + "." + parts[1] + ".*.*";
            }
        }
        if (normalized.contains(":")) {
            String[] parts = normalized.split(":");
            if (parts.length >= 2) {
                return parts[0] + ":" + parts[1] + ":*";
            }
        }
        return "***";
    }
}
