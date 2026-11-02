package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.MessageChangedEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
class MessageChangeDeliveryPublisher {
    private final NotificationSseEmitterRegistry streamRegistry;
    private final MessageRepository messageRepository;
    private final UserRepository userRepository;
    private final UserBlockRepository userBlockRepository;

    void publishAfterCommit(NotificationDeliveryJob job) {
        if (job.getNotificationType() != NotificationType.MESSAGE
                || job.getSourceType() != NotificationSourceType.MESSAGE) return;
        Long receiverId = job.getReceiverUserId();
        Long senderId = job.getActorUserId();
        Long messageId = job.getSourceId();
        if (receiverId == null || senderId == null || messageId == null
                || receiverId.equals(senderId)) return;
        if (userRepository.findByUserIdAndStatusAndDeletedAtIsNull(receiverId, User.STATUS_ACTIVE).isEmpty()
                || userBlockRepository.existsEitherDirection(receiverId, senderId)) return;
        // A delayed job must not revive a deleted message or target another user's mailbox.
        boolean accessible = messageRepository.findAccessibleMessage(receiverId, messageId, List.of())
                .filter(message -> receiverId.equals(message.getReceiver().getUserId())
                        && senderId.equals(message.getSender().getUserId()))
                .isPresent();
        if (!accessible) return;
        MessageChangedEvent event = new MessageChangedEvent(messageId, senderId);
        // This signal shares the existing durable outbox; a rolled-back delivery sends nothing.
        if (!TransactionSynchronizationManager.isSynchronizationActive()) return;
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    streamRegistry.publishMessageChanged(receiverId, event);
                } catch (RuntimeException exception) {
                    log.warn("Failed to deliver mailbox change SSE. userId={}, messageId={}, exceptionType={}",
                            receiverId, messageId, exception.getClass().getSimpleName());
                }
            }
        });
    }
}
