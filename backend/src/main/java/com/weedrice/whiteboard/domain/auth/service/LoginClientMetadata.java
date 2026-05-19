package com.weedrice.whiteboard.domain.auth.service;

public record LoginClientMetadata(String ipAddress, String userAgent) {

    public static LoginClientMetadata empty() {
        return new LoginClientMetadata(null, null);
    }
}
