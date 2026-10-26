package com.weedrice.whiteboard.domain.notification.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

@Data
public class PushSubscriptionStatusRequest {
    @NotBlank
    @Size(max = 500)
    private String endpoint;

    @Override
    public String toString() {
        return "PushSubscriptionStatusRequest{endpointPresent=" + (endpoint != null) + '}';
    }
}
