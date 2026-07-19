package com.weedrice.whiteboard.domain.notification.service;

import org.junit.jupiter.api.Test;
import tools.jackson.databind.ObjectMapper;

import static org.assertj.core.api.Assertions.assertThat;

class PushPayloadFactoryTest {

    @Test
    void usesStableNotificationTagForBrowserDisplayDeduplication() {
        PushPayloadFactory factory = new PushPayloadFactory(new ObjectMapper());
        PushDispatchCommand command = new PushDispatchCommand(
                3L,
                11L,
                7L,
                "content",
                "comment",
                "/board/free/post/7#comment-11");

        String first = factory.create(command);
        String second = factory.create(command);

        assertThat(first).isEqualTo(second)
                .contains("\"tag\":\"notification-7\"")
                .contains("\"url\":\"/board/free/post/7#comment-11\"")
                .contains("\"actorUserId\":11");
        assertThat(factory.readActorUserId(first)).isEqualTo(11L);
    }

    @Test
    void unsafeOrMissingTargetUrlFallsBackToNotificationInbox() {
        PushPayloadFactory factory = new PushPayloadFactory(new ObjectMapper());

        String external = factory.create(new PushDispatchCommand(
                3L, null, 7L, "content", "comment", "https://evil.example/path"));
        String protocolRelative = factory.create(new PushDispatchCommand(
                3L, null, 8L, "content", "comment", "//evil.example/path"));
        String missing = factory.create(new PushDispatchCommand(
                3L, null, 9L, "content", "comment", null));

        assertThat(external).contains("\"url\":\"/mypage/notifications\"");
        assertThat(protocolRelative).contains("\"url\":\"/mypage/notifications\"");
        assertThat(missing).contains("\"url\":\"/mypage/notifications\"");
    }
}
