package com.weedrice.whiteboard.domain.notification.service;

interface WebPushSender {
    int send(PushSubscriptionSnapshot subscription, String payload) throws Exception;
}
