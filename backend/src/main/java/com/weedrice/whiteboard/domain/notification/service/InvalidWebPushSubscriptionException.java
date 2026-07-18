package com.weedrice.whiteboard.domain.notification.service;

final class InvalidWebPushSubscriptionException extends RuntimeException {

    InvalidWebPushSubscriptionException(String reason) {
        super(reason);
    }
}
