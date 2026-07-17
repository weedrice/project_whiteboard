package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;

import java.time.LocalDateTime;

record PushSubscriptionSnapshot(
        Long subscriptionId,
        Long userId,
        String endpoint,
        String p256dh,
        String auth,
        LocalDateTime modifiedAt) {

    static PushSubscriptionSnapshot from(PushSubscription subscription) {
        return new PushSubscriptionSnapshot(
                subscription.getSubscriptionId(),
                subscription.getUser().getUserId(),
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth(),
                subscription.getModifiedAt());
    }
}
