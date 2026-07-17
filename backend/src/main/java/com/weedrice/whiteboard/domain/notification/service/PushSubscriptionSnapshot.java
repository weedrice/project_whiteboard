package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;

record PushSubscriptionSnapshot(
        Long subscriptionId,
        String endpoint,
        String p256dh,
        String auth) {

    static PushSubscriptionSnapshot from(PushSubscription subscription) {
        return new PushSubscriptionSnapshot(
                subscription.getSubscriptionId(),
                subscription.getEndpoint(),
                subscription.getP256dh(),
                subscription.getAuth());
    }
}
