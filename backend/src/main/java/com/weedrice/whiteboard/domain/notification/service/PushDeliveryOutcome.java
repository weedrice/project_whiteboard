package com.weedrice.whiteboard.domain.notification.service;

enum PushDeliveryOutcome {
    SUCCESS,
    EXPIRED,
    RETRYABLE_FAILURE,
    PERMANENT_FAILURE
}
