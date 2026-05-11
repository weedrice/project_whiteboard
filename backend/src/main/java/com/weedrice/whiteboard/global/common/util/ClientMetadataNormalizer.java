package com.weedrice.whiteboard.global.common.util;

public final class ClientMetadataNormalizer {

    public static final String UNKNOWN_IP_ADDRESS = "unknown";

    private static final int MAX_IP_ADDRESS_LENGTH = 45;

    private ClientMetadataNormalizer() {
    }

    public static String normalizeIpAddress(String ipAddress) {
        if (ipAddress == null || ipAddress.isBlank()) {
            return UNKNOWN_IP_ADDRESS;
        }

        String normalizedIpAddress = selectFirstAddress(ipAddress.trim());
        if (normalizedIpAddress.isBlank()) {
            return UNKNOWN_IP_ADDRESS;
        }
        return truncate(normalizedIpAddress, MAX_IP_ADDRESS_LENGTH);
    }

    private static String selectFirstAddress(String ipAddress) {
        int commaIndex = ipAddress.indexOf(',');
        if (commaIndex == -1) {
            return ipAddress;
        }
        return ipAddress.substring(0, commaIndex).trim();
    }

    private static String truncate(String value, int maxLength) {
        if (value.length() <= maxLength) {
            return value;
        }
        return value.substring(0, maxLength);
    }
}
