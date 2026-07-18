package com.weedrice.whiteboard.domain.notification.service;

import java.time.LocalDateTime;

record PushDeliveryLease(
        Long jobId,
        LocalDateTime claimedAt,
        PushSubscriptionSnapshot subscription,
        String payload) {

    boolean hasCompleteSnapshot() {
        return subscription != null
                && subscription.subscriptionId() != null
                && subscription.userId() != null
                && org.springframework.util.StringUtils.hasText(subscription.endpoint())
                && org.springframework.util.StringUtils.hasText(subscription.p256dh())
                && org.springframework.util.StringUtils.hasText(subscription.auth())
                && subscription.modifiedAt() != null
                && org.springframework.util.StringUtils.hasText(payload);
    }
}
