package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import com.weedrice.whiteboard.domain.notification.dto.PushPublicKeyResponse;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
public class PushPublicKeyService {

    private final WebPushProperties webPushProperties;

    public PushPublicKeyResponse getPublicKey() {
        boolean enabled = webPushProperties.isEnabled();
        return PushPublicKeyResponse.builder()
                .enabled(enabled)
                .publicKey(enabled ? webPushProperties.getPublicKey() : "")
                .build();
    }
}
