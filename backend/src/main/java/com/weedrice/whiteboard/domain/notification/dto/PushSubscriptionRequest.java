package com.weedrice.whiteboard.domain.notification.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushSubscriptionRequest {
    @NotBlank
    @Size(max = 500)
    private String endpoint;

    @Valid
    @NotNull
    private Keys keys;

    @Size(max = 255)
    private String userAgent;

    @Override
    public String toString() {
        return "PushSubscriptionRequest{endpointPresent=" + (endpoint != null)
                + ", keysPresent=" + (keys != null)
                + ", userAgentPresent=" + (userAgent != null) + '}';
    }

    @Data
    public static class Keys {
        @NotBlank
        @Size(max = 255)
        private String p256dh;

        @NotBlank
        @Size(max = 255)
        private String auth;

        @Override
        public String toString() {
            return "PushSubscriptionRequest.Keys{p256dhPresent=" + (p256dh != null)
                    + ", authPresent=" + (auth != null) + '}';
        }
    }
}
