package com.weedrice.whiteboard.domain.notification.repository;

public record KeywordFanoutPage(Long jobId, Long lastSubscriptionId, Integer retryCount) {
}
