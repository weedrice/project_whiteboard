package com.weedrice.whiteboard.domain.notification.service;

import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.concurrent.TimeoutException;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushNotificationDispatcherTest {

    @Mock
    private WebPushSender webPushSender;

    private PushNotificationDispatcher dispatcher;
    private SimpleMeterRegistry meterRegistry;
    private PushSubscriptionSnapshot subscription;

    @BeforeEach
    void setUp() {
        meterRegistry = new SimpleMeterRegistry();
        dispatcher = new PushNotificationDispatcher(webPushSender, meterRegistry);
        subscription = new PushSubscriptionSnapshot(
                11L, 1L, "https://push.example/subscription", "key", "auth",
                LocalDateTime.of(2026, 7, 18, 12, 0));
    }

    @Test
    void sendClassifiesSuccessAndExpiredResponses() throws Exception {
        when(webPushSender.send(subscription, "payload")).thenReturn(201, 410);

        assertThat(dispatcher.send(subscription, "payload")).isEqualTo(PushDeliveryOutcome.SUCCESS);
        assertThat(dispatcher.send(subscription, "payload")).isEqualTo(PushDeliveryOutcome.EXPIRED);
    }

    @Test
    void sendClassifiesRateLimitAndServerFailureAsRetryable() throws Exception {
        when(webPushSender.send(subscription, "payload")).thenReturn(429, 503);

        assertThat(dispatcher.send(subscription, "payload")).isEqualTo(PushDeliveryOutcome.RETRYABLE_FAILURE);
        assertThat(dispatcher.send(subscription, "payload")).isEqualTo(PushDeliveryOutcome.RETRYABLE_FAILURE);
    }

    @Test
    void sendClassifiesOtherClientFailureAsPermanent() throws Exception {
        when(webPushSender.send(subscription, "payload")).thenReturn(400);

        assertThat(dispatcher.send(subscription, "payload")).isEqualTo(PushDeliveryOutcome.PERMANENT_FAILURE);
    }

    @Test
    void timeoutIsRetryable() throws Exception {
        when(webPushSender.send(subscription, "payload"))
                .thenThrow(new WebPushDeliveryTimeoutException(new TimeoutException()));

        assertThat(dispatcher.send(subscription, "payload")).isEqualTo(PushDeliveryOutcome.RETRYABLE_FAILURE);
        assertThat(meterRegistry.counter("noviis.webpush.delivery", "outcome", "timeout").count()).isEqualTo(1);
    }

    @Test
    void invalidStoredSubscriptionIsExpiredWithoutRetry() throws Exception {
        when(webPushSender.send(subscription, "payload"))
                .thenThrow(new InvalidWebPushSubscriptionException("unsafe endpoint"));

        assertThat(dispatcher.send(subscription, "payload")).isEqualTo(PushDeliveryOutcome.EXPIRED);
        assertThat(meterRegistry.counter("noviis.webpush.delivery", "outcome", "expired").count()).isEqualTo(1);
    }
}
