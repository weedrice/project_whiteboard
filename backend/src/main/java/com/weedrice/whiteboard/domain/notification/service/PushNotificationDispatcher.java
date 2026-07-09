package com.weedrice.whiteboard.domain.notification.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import nl.martijndwars.webpush.PushService;
import org.apache.http.HttpResponse;
import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@Slf4j
@RequiredArgsConstructor
public class PushNotificationDispatcher {

    private static final String DEFAULT_TITLE = "NoviIs";
    private static final String DEFAULT_URL = "/mypage/notifications";

    private final WebPushProperties webPushProperties;
    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserSettingsRepository userSettingsRepository;
    private final ObjectMapper objectMapper;

    @Async("taskExecutor")
    @Transactional
    public void dispatch(Notification notification) {
        if (!webPushProperties.isEnabled()
                || notification == null
                || notification.getUser() == null
                || notification.getUser().getUserId() == null) {
            return;
        }
        PushPayload payload = PushPayload.from(notification);
        if (payload == null || !isPushEnabled(payload.userId())) {
            return;
        }
        List<PushSubscription> subscriptions = pushSubscriptionRepository.findByUser_UserId(payload.userId());
        if (subscriptions.isEmpty()) {
            return;
        }

        PushService pushService = createPushService();
        if (pushService == null) {
            return;
        }
        String body = toJson(payload);
        for (PushSubscription subscription : subscriptions) {
            sendBestEffort(pushService, subscription, body);
        }
    }

    private void sendBestEffort(PushService pushService, PushSubscription subscription, String body) {
        try {
            HttpResponse response = pushService.send(new nl.martijndwars.webpush.Notification(
                    subscription.getEndpoint(),
                    subscription.getP256dh(),
                    subscription.getAuth(),
                    body));
            int statusCode = response.getStatusLine().getStatusCode();
            if (statusCode == 404 || statusCode == 410) {
                pushSubscriptionRepository.delete(subscription);
            } else if (statusCode >= 400) {
                log.warn("Failed to send web push. subscriptionId={}, statusCode={}",
                        subscription.getSubscriptionId(), statusCode);
            }
        } catch (Exception ex) {
            log.warn("Failed to send web push. subscriptionId={}, exceptionType={}",
                    subscription.getSubscriptionId(), ex.getClass().getSimpleName());
        }
    }

    private PushService createPushService() {
        try {
            return new PushService(
                    webPushProperties.getPublicKey(),
                    webPushProperties.getPrivateKey(),
                    webPushProperties.getSubject());
        } catch (Exception ex) {
            log.warn("Failed to create web push service. exceptionType={}", ex.getClass().getSimpleName());
            return null;
        }
    }

    private boolean isPushEnabled(Long userId) {
        return userSettingsRepository.findSettingsReadByUserId(userId)
                .map(settings -> Boolean.TRUE.equals(settings.getPushEnabled()))
                .orElse(false);
    }

    private String toJson(PushPayload payload) {
        try {
            return objectMapper.writeValueAsString(payload);
        } catch (JsonProcessingException ex) {
            return "{\"title\":\"" + DEFAULT_TITLE + "\",\"body\":\"" + DEFAULT_TITLE + "\",\"url\":\"" + DEFAULT_URL + "\"}";
        }
    }

    private record PushPayload(
            Long userId,
            String title,
            String body,
            String url,
            String tag) {
        static PushPayload from(Notification notification) {
            String notificationType = notification.getNotificationType() == null
                    ? "notification"
                    : notification.getNotificationType().name().toLowerCase();
            String tag = notification.getNotificationId() == null
                    ? notificationType
                    : "notification-" + notification.getNotificationId();
            return new PushPayload(
                    notification.getUser().getUserId(),
                    DEFAULT_TITLE,
                    notification.getContent(),
                    DEFAULT_URL,
                    tag);
        }
    }
}
