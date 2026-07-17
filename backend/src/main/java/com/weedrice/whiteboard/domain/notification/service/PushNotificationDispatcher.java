package com.weedrice.whiteboard.domain.notification.service;

import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class PushNotificationDispatcher {

    private final WebPushSender webPushSender;
    private final MeterRegistry meterRegistry;

    PushDeliveryOutcome send(PushSubscriptionSnapshot subscription, String payload) {
        try {
            int statusCode = webPushSender.send(subscription, payload);
            if (statusCode == 404 || statusCode == 410) {
                recordDelivery("expired");
                return PushDeliveryOutcome.EXPIRED;
            }
            if (statusCode >= 200 && statusCode < 300) {
                recordDelivery("success");
                return PushDeliveryOutcome.SUCCESS;
            }
            boolean retryable = statusCode == 429 || statusCode >= 500;
            recordDelivery(retryable ? "retryable_failure" : "permanent_failure");
            log.warn("Failed to send web push. subscriptionId={}, statusCode={}",
                    subscription.subscriptionId(), statusCode);
            return retryable ? PushDeliveryOutcome.RETRYABLE_FAILURE : PushDeliveryOutcome.PERMANENT_FAILURE;
        } catch (WebPushDeliveryTimeoutException exception) {
            recordDelivery("timeout");
            log.warn("Web push delivery timed out. subscriptionId={}", subscription.subscriptionId());
            return PushDeliveryOutcome.RETRYABLE_FAILURE;
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordDelivery("retryable_failure");
            log.warn("Web push delivery interrupted. subscriptionId={}", subscription.subscriptionId());
            return PushDeliveryOutcome.RETRYABLE_FAILURE;
        } catch (Exception exception) {
            recordDelivery("retryable_failure");
            log.warn("Failed to send web push. subscriptionId={}, exceptionType={}",
                    subscription.subscriptionId(), exception.getClass().getSimpleName());
            return PushDeliveryOutcome.RETRYABLE_FAILURE;
        }
    }

    private void recordDelivery(String outcome) {
        meterRegistry.counter("noviis.webpush.delivery", "outcome", outcome).increment();
    }

}
