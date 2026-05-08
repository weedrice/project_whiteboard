package com.weedrice.whiteboard.domain.auth.service;

final class LoginClientMetadataNormalizer {

    static final String UNKNOWN_IP_ADDRESS = "unknown";

    private static final int MAX_IP_ADDRESS_LENGTH = 45;
    private static final int MAX_USER_AGENT_LENGTH = 500;
    private static final int MAX_DEVICE_INFO_LENGTH = 255;

    private LoginClientMetadataNormalizer() {
    }

    static String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return UNKNOWN_IP_ADDRESS;
        }
        return truncate(ipAddress.trim(), MAX_IP_ADDRESS_LENGTH);
    }

    static String normalizeUserAgent(String userAgent) {
        if (userAgent == null) {
            return null;
        }
        return truncate(userAgent, MAX_USER_AGENT_LENGTH);
    }

    static String normalizeDeviceInfo(String deviceInfo) {
        if (deviceInfo == null) {
            return null;
        }
        return truncate(deviceInfo, MAX_DEVICE_INFO_LENGTH);
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
