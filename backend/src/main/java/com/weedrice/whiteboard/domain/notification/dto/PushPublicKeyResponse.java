package com.weedrice.whiteboard.domain.notification.dto;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PushPublicKeyResponse {
    private final String publicKey;
    private final boolean enabled;
}
