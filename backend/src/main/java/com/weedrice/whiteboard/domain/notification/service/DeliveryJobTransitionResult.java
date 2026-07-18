package com.weedrice.whiteboard.domain.notification.service;

enum DeliveryJobTransitionResult {
    APPLIED_SUCCESS,
    APPLIED_RETRY,
    APPLIED_DEAD_LETTER,
    REJECTED_INVALID,
    LEASE_LOST
}
