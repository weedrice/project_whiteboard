package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InOrder;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationAccessInvalidationServiceTest {

    @Mock PushSubscriptionRepository subscriptions;
    @Mock ApplicationEventPublisher eventPublisher;
    @Mock NotificationStreamControl streamControl;

    @Test
    void revokeDeletesPushSubscriptionsBeforePublishingDisconnect() {
        NotificationAccessInvalidationService service =
                new NotificationAccessInvalidationService(subscriptions, eventPublisher);

        service.revokeForLockedUser(7L);

        InOrder inOrder = inOrder(subscriptions, eventPublisher);
        inOrder.verify(subscriptions).deleteAllByUserId(7L);
        inOrder.verify(eventPublisher).publishEvent(NotificationStreamInvalidationEvent.disconnectUser(7L));
    }

    @Test
    void listenerRoutesInvalidationsToRegistryControl() {
        NotificationStreamInvalidationListener listener = new NotificationStreamInvalidationListener(streamControl);

        listener.handle(NotificationStreamInvalidationEvent.disconnectUser(1L));
        listener.handle(NotificationStreamInvalidationEvent.invalidatePost(2L));
        listener.handle(NotificationStreamInvalidationEvent.invalidateBoard(4L));
        listener.handle(NotificationStreamInvalidationEvent.invalidateUserTopics(3L));

        verify(streamControl).disconnectUser(1L);
        verify(streamControl).invalidateCommentTopic(2L);
        verify(streamControl).invalidateCommentTopicsForBoard(4L);
        verify(streamControl).invalidateCommentTopicsForUser(3L);
    }

    @Test
    void boardInvalidationPublishesTransactionalEvent() {
        NotificationAccessInvalidationService service =
                new NotificationAccessInvalidationService(subscriptions, eventPublisher);

        service.invalidateCommentTopicsForBoardAfterCommit(4L);

        verify(eventPublisher).publishEvent(NotificationStreamInvalidationEvent.invalidateBoard(4L));
    }
}
