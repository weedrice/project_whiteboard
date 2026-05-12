package com.weedrice.whiteboard.domain.auth.service;

final class LoginClientMetadataNormalizer {

    static final String UNKNOWN_IP_ADDRESS = "unknown";
    static final String UNKNOWN_LOGIN_ID = "unknown";

    private static final int MAX_LOGIN_ID_LENGTH = 30;
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

    static String normalizeLoginId(String loginId) {
        if (loginId == null || loginId.isBlank()) {
            return UNKNOWN_LOGIN_ID;
        }
        return truncate(loginId.trim(), MAX_LOGIN_ID_LENGTH);
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
