package com.weedrice.whiteboard.domain.notification.service;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

@Component
@RequiredArgsConstructor
class NotificationStreamInvalidationListener {

    private final NotificationStreamControl streamControl;

    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT)
    void handle(NotificationStreamInvalidationEvent event) {
        switch (event.type()) {
            case DISCONNECT_USER -> streamControl.disconnectUser(event.targetId());
            case INVALIDATE_POST -> streamControl.invalidateCommentTopic(event.targetId());
            case INVALIDATE_USER_TOPICS -> streamControl.invalidateCommentTopicsForUser(event.targetId());
        }
    }
}
