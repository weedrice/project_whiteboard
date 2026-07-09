package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class PushPublicKeyServiceTest {

    @Test
    void getPublicKey_returnsEnabledKeyWhenVapidConfigured() {
        WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey("public-key");
        properties.setPrivateKey("private-key");
        properties.setSubject("mailto:admin@example.com");
        PushPublicKeyService service = new PushPublicKeyService(properties);

        var response = service.getPublicKey();

        assertThat(response.isEnabled()).isTrue();
        assertThat(response.getPublicKey()).isEqualTo("public-key");
    }

    @Test
    void getPublicKey_returnsDisabledWhenVapidMissing() {
        WebPushProperties properties = new WebPushProperties();
        properties.setPublicKey("public-key");
        PushPublicKeyService service = new PushPublicKeyService(properties);

        var response = service.getPublicKey();

        assertThat(response.isEnabled()).isFalse();
        assertThat(response.getPublicKey()).isEmpty();
    }
}
