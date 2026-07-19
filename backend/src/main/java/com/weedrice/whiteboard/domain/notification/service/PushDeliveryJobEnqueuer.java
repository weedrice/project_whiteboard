package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
class PushDeliveryJobEnqueuer {

    private final WebPushProperties webPushProperties;
    private final PushDispatchSnapshotReader snapshotReader;
    private final PushDeliveryJobRepository jobRepository;
    private final PushPayloadFactory payloadFactory;
    private final NotificationTargetUrlResolver targetUrlResolver;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public int enqueue(NotificationEvent event, Notification notification) {
        if (!webPushProperties.isEnabled() || event == null || event.getEventId() == null || notification == null) {
            return 0;
        }
        PushDispatchCommand command = PushDispatchCommand.from(notification, resolveTargetUrl(notification));
        if (command == null) {
            return 0;
        }
        List<PushSubscriptionSnapshot> subscriptions = snapshotReader.loadEnabledSubscriptions(command.userId());
        if (subscriptions.isEmpty()) {
            return 0;
        }
        String payload = payloadFactory.create(command);
        LocalDateTime now = LocalDateTime.now(clock);
        return (int) subscriptions.stream()
                .map(subscription -> PushDeliveryJob.pending(
                        event.getEventId(),
                        subscription.subscriptionId(),
                        subscription.userId(),
                        subscription.endpoint(),
                        subscription.p256dh(),
                        subscription.auth(),
                        subscription.modifiedAt(),
                        payload,
                        now))
                .filter(jobRepository::insertIfAbsent)
                .count();
    }

    private String resolveTargetUrl(Notification notification) {
        try {
            return targetUrlResolver.resolve(notification);
        } catch (RuntimeException exception) {
            log.warn("Failed to resolve push target URL. notificationId={}, exceptionType={}",
                    notification != null ? notification.getNotificationId() : null,
                    exception.getClass().getSimpleName());
            return null;
        }
    }
}
