package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
class PushDeliveryJobEnqueuer {

    private final WebPushProperties webPushProperties;
    private final PushDispatchSnapshotReader snapshotReader;
    private final PushDeliveryJobRepository jobRepository;
    private final PushPayloadFactory payloadFactory;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    public int enqueue(NotificationEvent event, Notification notification) {
        PushDispatchCommand command = PushDispatchCommand.from(notification);
        if (!webPushProperties.isEnabled() || event == null || event.getEventId() == null || command == null) {
            return 0;
        }
        List<PushSubscriptionSnapshot> subscriptions = snapshotReader.loadEnabledSubscriptions(command.userId());
        if (subscriptions.isEmpty()) {
            return 0;
        }
        String payload = payloadFactory.create(command);
        LocalDateTime now = LocalDateTime.now(clock);
        List<PushDeliveryJob> jobs = subscriptions.stream()
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
                .toList();
        jobRepository.saveAll(jobs);
        return jobs.size();
    }
}
