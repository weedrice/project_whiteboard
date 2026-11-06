package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.message.entity.Message;
import com.weedrice.whiteboard.domain.message.repository.MessageRepository;
import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.MessageChangedEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.repository.NotificationDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.notification.web.NotificationSseEmitterRegistry;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import com.weedrice.whiteboard.domain.user.repository.UserNotificationSettingsRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.context.MessageSource;
import org.springframework.test.util.ReflectionTestUtils;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class MessageChangeDeliveryPublisherTest {
    private final NotificationSseEmitterRegistry registry = mock(NotificationSseEmitterRegistry.class);
    private final MessageRepository messages = mock(MessageRepository.class);
    private final UserRepository users = mock(UserRepository.class);
    private final UserBlockRepository blocks = mock(UserBlockRepository.class);
    private final MessageChangeDeliveryPublisher publisher =
            new MessageChangeDeliveryPublisher(registry, messages, users, blocks);
    private final User receiver = user(1L);
    private final User sender = user(2L);
    private final LocalDateTime claimedAt = LocalDateTime.of(2026, 11, 2, 0, 0);

    @BeforeEach
    void startTransaction() {
        TransactionSynchronizationManager.initSynchronization();
        TransactionSynchronizationManager.setActualTransactionActive(true);
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(1L, User.STATUS_ACTIVE))
                .thenReturn(Optional.of(receiver));
        when(messages.findAccessibleMessage(1L, 8L, List.of()))
                .thenReturn(Optional.of(Message.builder().sender(sender).receiver(receiver).content("private").build()));
    }

    @AfterEach
    void clearTransaction() {
        TransactionSynchronizationManager.clear();
    }

    @Test
    void disabledNotificationStillPublishesMailboxChangeOnlyAfterDeliveryCommit() {
        NotificationDeliveryJobRepository jobs = mock(NotificationDeliveryJobRepository.class);
        NotificationRepository notifications = mock(NotificationRepository.class);
        UserNotificationSettingsRepository settings = mock(UserNotificationSettingsRepository.class);
        PushDeliveryJobEnqueuer pushes = mock(PushDeliveryJobEnqueuer.class);
        when(settings.findByUserIdAndNotificationType(1L, NotificationType.MESSAGE))
                .thenReturn(Optional.of(UserNotificationSettings.builder()
                        .userId(1L).notificationType(NotificationType.MESSAGE).isEnabled(false).build()));
        when(users.findByIdForUpdate(1L)).thenReturn(Optional.of(receiver));
        NotificationCommandService command = new NotificationCommandService(notifications,
                new NotificationPreferenceService(settings), users, pushes,
                mock(UserSettingsRepository.class), mock(MessageSource.class));
        EntityManager entityManager = mock(EntityManager.class);
        when(entityManager.getReference(User.class, 1L)).thenReturn(receiver);
        when(entityManager.getReference(User.class, 2L)).thenReturn(sender);
        NotificationDeliveryPublisher notificationPublisher = mock(NotificationDeliveryPublisher.class);
        NotificationDeliveryJob job = job();
        job.claim(claimedAt);
        when(jobs.findByIdForUpdate(9L)).thenReturn(Optional.of(job));
        NotificationDeliveryJobTransaction transaction = new NotificationDeliveryJobTransaction(
                jobs, command, notificationPublisher, publisher, entityManager,
                new NotificationPayloadValidator(), Clock.systemUTC(),
                new NotificationSourceDeliveryPolicy(new KeywordNotificationDeliveryPolicy(mock(com.weedrice.whiteboard.domain.post.repository.PostRepository.class),
                        mock(com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository.class), blocks), mock(MentionNotificationDeliveryPolicy.class)));

        assertThat(transaction.deliver(9L, claimedAt)).isEqualTo(DeliveryJobTransitionResult.APPLIED_SUCCESS);
        assertThat(job.getStatus()).isEqualTo(NotificationDeliveryJob.Status.COMPLETED);
        verifyNoInteractions(registry, notifications, pushes, notificationPublisher);

        TransactionSynchronizationManager.getSynchronizations().forEach(TransactionSynchronization::afterCommit);

        verify(registry).publishMessageChanged(1L, new MessageChangedEvent(8L, 2L));
        // Completed jobs cannot publish twice on a repeated delivery attempt.
        assertThat(transaction.deliver(9L, claimedAt)).isEqualTo(DeliveryJobTransitionResult.LEASE_LOST);
    }

    @Test
    void rollbackDoesNotPublish() {
        publisher.publishAfterCommit(job());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).hasSize(1);
        TransactionSynchronizationManager.getSynchronizations()
                .forEach(synchronization -> synchronization.afterCompletion(TransactionSynchronization.STATUS_ROLLED_BACK));
        verifyNoInteractions(registry);
    }

    @Test
    void inactiveReceiverIsNotNotified() {
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(1L, User.STATUS_ACTIVE)).thenReturn(Optional.empty());
        publisher.publishAfterCommit(job());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void blockedPartnerIsNotNotified() {
        when(blocks.existsEitherDirection(1L, 2L)).thenReturn(true);
        publisher.publishAfterCommit(job());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void deletedMessageIsNotNotified() {
        when(messages.findAccessibleMessage(1L, 8L, List.of())).thenReturn(Optional.empty());
        publisher.publishAfterCommit(job());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void mismatchedRecipientIsNotNotified() {
        when(messages.findAccessibleMessage(1L, 8L, List.of()))
                .thenReturn(Optional.of(Message.builder().sender(receiver).receiver(sender).content("other").build()));
        publisher.publishAfterCommit(job());
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
    }

    @Test
    void selfMessageAndOtherNotificationTypesAreIgnored() {
        NotificationDeliveryJob self = NotificationDeliveryJob.from(new NotificationEvent(
                receiver, receiver, NotificationType.MESSAGE, NotificationSourceType.MESSAGE, 8L, "self"), claimedAt);
        NotificationDeliveryJob other = NotificationDeliveryJob.from(new NotificationEvent(
                receiver, sender, NotificationType.COMMENT, NotificationSourceType.POST, 8L, "comment"), claimedAt);
        publisher.publishAfterCommit(self);
        publisher.publishAfterCommit(other);
        assertThat(TransactionSynchronizationManager.getSynchronizations()).isEmpty();
        verify(messages, never()).findAccessibleMessage(any(), any(), any());
    }

    private NotificationDeliveryJob job() {
        return NotificationDeliveryJob.from(new NotificationEvent(
                receiver, sender, NotificationType.MESSAGE, NotificationSourceType.MESSAGE, 8L, "private"), claimedAt);
    }

    private static User user(Long id) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", id);
        return user;
    }
}
