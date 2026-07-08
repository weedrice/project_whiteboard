package com.weedrice.whiteboard.domain.notification.dto;

import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
public class PushSubscriptionResponse {
    private Long subscriptionId;
    private String endpoint;

    public static PushSubscriptionResponse from(PushSubscription subscription) {
        return PushSubscriptionResponse.builder()
                .subscriptionId(subscription.getSubscriptionId())
                .endpoint(subscription.getEndpoint())
                .build();
    }
}
