package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import io.micrometer.core.instrument.MeterRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import tools.jackson.core.JacksonException;
import tools.jackson.databind.ObjectMapper;

import java.util.ArrayList;
import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PushNotificationDispatcher {

    private static final String DEFAULT_TITLE = "NoviIs";
    private static final String DEFAULT_URL = "/mypage/notifications";

    private final WebPushProperties webPushProperties;
    private final PushDispatchSnapshotReader snapshotReader;
    private final PushSubscriptionCleanupService cleanupService;
    private final WebPushSender webPushSender;
    private final ObjectMapper objectMapper;
    private final MeterRegistry meterRegistry;

    @Async("notificationTaskExecutor")
    public void dispatch(PushDispatchCommand command) {
        if (!webPushProperties.isEnabled() || !isValid(command)) {
            return;
        }
        List<PushSubscriptionSnapshot> subscriptions = snapshotReader.loadEnabledSubscriptions(command.userId());
        if (subscriptions.isEmpty()) {
            return;
        }

        String payload = toJson(PushPayload.from(command));
        List<PushSubscriptionSnapshot> expiredSubscriptions = new ArrayList<>();
        for (PushSubscriptionSnapshot subscription : subscriptions) {
            sendBestEffort(subscription, payload, expiredSubscriptions);
        }
        cleanupExpiredSubscriptions(expiredSubscriptions);
    }

    private void sendBestEffort(
            PushSubscriptionSnapshot subscription,
            String payload,
            List<PushSubscriptionSnapshot> expiredSubscriptions) {
        try {
            int statusCode = webPushSender.send(subscription, payload);
            if (statusCode == 404 || statusCode == 410) {
                expiredSubscriptions.add(subscription);
                recordDelivery("expired");
            } else if (statusCode >= 200 && statusCode < 300) {
                recordDelivery("success");
            } else {
                recordDelivery("failure");
                log.warn("Failed to send web push. subscriptionId={}, statusCode={}",
                        subscription.subscriptionId(), statusCode);
            }
        } catch (WebPushDeliveryTimeoutException exception) {
            recordDelivery("timeout");
            log.warn("Web push delivery timed out. subscriptionId={}", subscription.subscriptionId());
        } catch (InterruptedException exception) {
            Thread.currentThread().interrupt();
            recordDelivery("failure");
            log.warn("Web push delivery interrupted. subscriptionId={}", subscription.subscriptionId());
        } catch (Exception exception) {
            recordDelivery("failure");
            log.warn("Failed to send web push. subscriptionId={}, exceptionType={}",
                    subscription.subscriptionId(), exception.getClass().getSimpleName());
        }
    }

    private void cleanupExpiredSubscriptions(List<PushSubscriptionSnapshot> expiredSubscriptions) {
        if (expiredSubscriptions.isEmpty()) {
            return;
        }
        try {
            int deleted = cleanupService.deleteExpiredSubscriptions(expiredSubscriptions);
            meterRegistry.counter("noviis.webpush.subscription.cleanup", "outcome", "deleted")
                    .increment(deleted);
        } catch (RuntimeException exception) {
            meterRegistry.counter("noviis.webpush.subscription.cleanup", "outcome", "failure").increment();
            log.warn("Failed to delete expired web push subscriptions. count={}, exceptionType={}",
                    expiredSubscriptions.size(), exception.getClass().getSimpleName());
        }
    }

    private boolean isValid(PushDispatchCommand command) {
        return command != null && command.userId() != null;
    }

    private void recordDelivery(String outcome) {
        meterRegistry.counter("noviis.webpush.delivery", "outcome", outcome).increment();
    }

    private String toJson(PushPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JacksonException exception) {
            return "{\"title\":\"" + DEFAULT_TITLE + "\",\"body\":\"" + DEFAULT_TITLE
                    + "\",\"url\":\"" + DEFAULT_URL + "\"}";
        }
    }

    private record PushPayload(
            String title,
            String body,
            String url,
            String tag) {
        static PushPayload from(PushDispatchCommand command) {
            String tag = command.notificationId() == null
                    ? command.notificationType()
                    : "notification-" + command.notificationId();
            return new PushPayload(DEFAULT_TITLE, command.content(), DEFAULT_URL, tag);
        }
    }
}
