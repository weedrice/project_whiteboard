package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.UUID;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationAccessInvalidationServiceTest {

    private static final UUID FAMILY_ID = UUID.fromString("00000000-0000-0000-0000-000000000021");

    @Mock PushSubscriptionRepository subscriptions;
    @Mock PushDeliveryJobRepository jobs;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock NotificationStreamControl streamControl;

    @Test
    void revokeDeletesPushSubscriptionsBeforePublishingDisconnect() {
        NotificationAccessInvalidationService service =
                new NotificationAccessInvalidationService(subscriptions, jobs, eventPublisher);

        service.revokeForLockedUser(7L);

        InOrder inOrder = inOrder(jobs, subscriptions, eventPublisher);
        inOrder.verify(jobs).redactForReceiver(7L);
        inOrder.verify(subscriptions).deleteAllByUserId(7L);
        inOrder.verify(eventPublisher).publishEvent(NotificationStreamInvalidationEvent.disconnectUser(7L));
    }

    @Test
    void listenerRoutesInvalidationsToRegistryControl() {
        NotificationStreamInvalidationListener listener = new NotificationStreamInvalidationListener(streamControl);

        listener.handle(NotificationStreamInvalidationEvent.disconnectUser(1L));
        listener.handle(NotificationStreamInvalidationEvent.disconnectSessionFamily(1L, FAMILY_ID));
        listener.handle(NotificationStreamInvalidationEvent.disconnectOtherSessionFamilies(1L, FAMILY_ID));
        listener.handle(NotificationStreamInvalidationEvent.invalidatePost(2L));
        listener.handle(NotificationStreamInvalidationEvent.invalidateBoard(4L));
        listener.handle(NotificationStreamInvalidationEvent.invalidateUserTopics(3L));

        verify(streamControl).disconnectUser(1L);
        verify(streamControl).disconnectSessionFamily(1L, FAMILY_ID);
        verify(streamControl).disconnectOtherSessionFamilies(1L, FAMILY_ID);
        verify(streamControl).invalidateCommentTopic(2L);
        verify(streamControl).invalidateCommentTopicsForBoard(4L);
        verify(streamControl).invalidateCommentTopicsForUser(3L);
    }

    @Test
    void familyDisconnectPublishesTransactionalEvent() {
        NotificationAccessInvalidationService service =
                new NotificationAccessInvalidationService(subscriptions, jobs, eventPublisher);

        service.disconnectSessionFamilyAfterCommit(7L, FAMILY_ID);

        verify(eventPublisher).publishEvent(
                NotificationStreamInvalidationEvent.disconnectSessionFamily(7L, FAMILY_ID));
    }

    @Test
    void boardInvalidationPublishesTransactionalEvent() {
        NotificationAccessInvalidationService service =
                new NotificationAccessInvalidationService(subscriptions, jobs, eventPublisher);

        service.invalidateCommentTopicsForBoardAfterCommit(4L);

        verify(eventPublisher).publishEvent(NotificationStreamInvalidationEvent.invalidateBoard(4L));
    }
}
