package com.weedrice.whiteboard.domain.notification.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PushPayloadFactoryTest {

    @Test
    void usesStableNotificationTagForBrowserDisplayDeduplication() {
        PushPayloadFactory factory = new PushPayloadFactory(new ObjectMapper());
        PushDispatchCommand command = new PushDispatchCommand(3L, 7L, "content", "comment");

        String first = factory.create(command);
        String second = factory.create(command);

        assertThat(first).isEqualTo(second).contains("\"tag\":\"notification-7\"");
    }
}
