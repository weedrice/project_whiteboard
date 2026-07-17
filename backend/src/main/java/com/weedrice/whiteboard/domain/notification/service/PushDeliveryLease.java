package com.weedrice.whiteboard.domain.notification.service;

import java.time.LocalDateTime;

record PushDeliveryLease(
        Long jobId,
        LocalDateTime claimedAt,
        PushSubscriptionSnapshot subscription,
        String payload) {
}
