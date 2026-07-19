package com.weedrice.whiteboard.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;
import tools.jackson.databind.JsonNode;

import java.net.URI;

@Component
@RequiredArgsConstructor
class PushPayloadFactory {

    private static final String DEFAULT_TITLE = "NoviIs";
    private static final String DEFAULT_URL = "/mypage/notifications";

    private final ObjectMapper objectMapper;

    String create(PushDispatchCommand command) {
        try {
            return objectMapper.writeValueAsString(PushPayload.from(command));
        } catch (JacksonException exception) {
            return "{\"title\":\"" + DEFAULT_TITLE + "\",\"body\":\"" + DEFAULT_TITLE
                    + "\",\"url\":\"" + DEFAULT_URL + "\"}";
        }
    }

    Long readActorUserId(String payload) {
        if (payload == null || payload.isBlank()) {
            return null;
        }
        try {
            JsonNode actorUserId = objectMapper.readTree(payload).get("actorUserId");
            return actorUserId != null && actorUserId.isIntegralNumber() ? actorUserId.longValue() : null;
        } catch (JacksonException exception) {
            return null;
        }
    }

    private record PushPayload(String title, String body, String url, String tag, Long actorUserId) {
        private static PushPayload from(PushDispatchCommand command) {
            String tag = command.notificationId() == null
                    ? command.notificationType()
                    : "notification-" + command.notificationId();
            return new PushPayload(
                    DEFAULT_TITLE,
                    command.content(),
                    safeTargetUrl(command.targetUrl()),
                    tag,
                    command.actorUserId());
        }

        private static String safeTargetUrl(String targetUrl) {
            if (targetUrl == null || targetUrl.isBlank() || !targetUrl.startsWith("/")
                    || targetUrl.startsWith("//") || targetUrl.indexOf('\r') >= 0 || targetUrl.indexOf('\n') >= 0) {
                return DEFAULT_URL;
            }
            try {
                URI uri = URI.create(targetUrl);
                return uri.isAbsolute() || uri.getAuthority() != null ? DEFAULT_URL : targetUrl;
            } catch (IllegalArgumentException exception) {
                return DEFAULT_URL;
            }
        }
    }
}
