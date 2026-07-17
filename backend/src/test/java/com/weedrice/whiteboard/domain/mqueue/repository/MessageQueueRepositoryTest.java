package com.weedrice.whiteboard.domain.mqueue.repository;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(com.weedrice.whiteboard.global.config.QuerydslConfig.class)
class MessageQueueRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private MessageQueueRepository messageQueueRepository;

    @Test
    @DisplayName("claimForProcessing sets processing_started_at when pending message is claimed")
    void claimForProcessing_setsProcessingStartedAt() {
        MessageQueue message = persistMessageQueue();
        LocalDateTime claimedAt = LocalDateTime.of(2026, 4, 22, 13, 30);

        int updated = messageQueueRepository.claimForProcessing(
                message.getQueueId(), MessageQueuePolicy.MAX_RETRY_COUNT, claimedAt);

        entityManager.flush();
        entityManager.clear();

        MessageQueue claimed = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(claimed.getStatus()).isEqualTo("PROCESSING");
        assertThat(claimed.getProcessingStartedAt()).isEqualTo(claimedAt);
    }

    @Test
    @DisplayName("renewProcessingLeaseIfCurrent extends only the current processing lease")
    void renewProcessingLeaseIfCurrent_extendsCurrentProcessingLease() {
        MessageQueue message = persistMessageQueue();
        LocalDateTime processingStartedAt = LocalDateTime.of(2026, 4, 22, 13, 30);
        LocalDateTime renewedAt = LocalDateTime.of(2026, 4, 22, 13, 31);
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", processingStartedAt);
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.renewProcessingLeaseIfCurrent(
                message.getQueueId(), processingStartedAt, renewedAt);

        entityManager.flush();
        entityManager.clear();

        MessageQueue renewed = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(renewed.getStatus()).isEqualTo("PROCESSING");
        assertThat(renewed.getProcessingStartedAt()).isEqualTo(renewedAt);
    }

    @Test
    @DisplayName("renewProcessingLeaseIfCurrent skips changed lease")
    void renewProcessingLeaseIfCurrent_skipsChangedLease() {
        MessageQueue message = persistMessageQueue();
        LocalDateTime currentLease = LocalDateTime.of(2026, 4, 22, 13, 30);
        LocalDateTime staleLease = LocalDateTime.of(2026, 4, 22, 13, 29);
        LocalDateTime renewedAt = LocalDateTime.of(2026, 4, 22, 13, 31);
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", currentLease);
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.renewProcessingLeaseIfCurrent(
                message.getQueueId(), staleLease, renewedAt);

        entityManager.flush();
        entityManager.clear();

        MessageQueue unchanged = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isZero();
        assertThat(unchanged.getStatus()).isEqualTo("PROCESSING");
        assertThat(unchanged.getProcessingStartedAt()).isEqualTo(currentLease);
    }

    @Test
    @DisplayName("recordSendAttemptIfCurrent stores attempt ledger for current processing lease")
    void recordSendAttemptIfCurrent_storesAttemptLedger() {
        MessageQueue message = persistMessageQueue();
        LocalDateTime processingStartedAt = LocalDateTime.of(2026, 4, 22, 13, 30);
        LocalDateTime attemptStartedAt = LocalDateTime.of(2026, 4, 22, 13, 31);
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", processingStartedAt);
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.recordSendAttemptIfCurrent(
                message.getQueueId(),
                processingStartedAt,
                "attempt-1",
                attemptStartedAt);

        entityManager.flush();
        entityManager.clear();

        MessageQueue recorded = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recorded.getSendAttemptId()).isEqualTo("attempt-1");
        assertThat(recorded.getSendAttemptStartedAt()).isEqualTo(attemptStartedAt);
        assertThat(recorded.getDeliveryUncertainAt()).isEqualTo(attemptStartedAt);
    }

    @Test
    @DisplayName("recoverStaleProcessingMessages increments retry count and releases stale processing lease")
    void recoverStaleProcessingMessages_requeuesStaleProcessingMessageWithinRetryBudget() {
        MessageQueue message = persistMessageQueue();
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", LocalDateTime.now().minusMinutes(10));
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.recoverStaleProcessingMessages(
                LocalDateTime.now().minusMinutes(5),
                MessageQueuePolicy.MAX_RETRY_COUNT,
                LocalDateTime.now());

        entityManager.flush();
        entityManager.clear();

        MessageQueue recovered = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo("PENDING");
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("recoverStaleProcessingMessages quarantines legacy send attempt rows without delivery marker")
    void recoverStaleProcessingMessages_quarantinesLegacySendAttemptRowsWithoutDeliveryMarker() {
        MessageQueue message = persistMessageQueue();
        LocalDateTime attemptStartedAt = LocalDateTime.of(2026, 4, 22, 13, 30);
        LocalDateTime staleBefore = attemptStartedAt.plusMinutes(5);
        LocalDateTime recoveredAt = LocalDateTime.of(2026, 4, 22, 13, 35);
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", attemptStartedAt);
        ReflectionTestUtils.setField(message, "sendAttemptId", "attempt-1");
        ReflectionTestUtils.setField(message, "sendAttemptStartedAt", attemptStartedAt);
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.recoverStaleProcessingMessages(
                staleBefore,
                MessageQueuePolicy.MAX_RETRY_COUNT,
                recoveredAt);

        entityManager.flush();
        entityManager.clear();

        MessageQueue recovered = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo(MessageQueue.STATUS_DELIVERED_UNCONFIRMED);
        assertThat(recovered.getRetryCount()).isZero();
        assertThat(recovered.getProcessingStartedAt()).isNull();
        assertThat(recovered.getSendAttemptId()).isEqualTo("attempt-1");
        assertThat(recovered.getSendAttemptStartedAt()).isEqualTo(attemptStartedAt);
        assertThat(recovered.getDeliveryUncertainAt()).isEqualTo(recoveredAt);
    }

    @Test
    @DisplayName("recoverStaleProcessingMessages preserves rows with uncertain delivery marker")
    void recoverStaleProcessingMessages_preservesRowsWithUncertainDeliveryMarker() {
        MessageQueue message = persistMessageQueue();
        LocalDateTime attemptStartedAt = LocalDateTime.now().minusMinutes(10);
        LocalDateTime recoveredAt = LocalDateTime.of(2026, 4, 22, 13, 35);
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", attemptStartedAt);
        ReflectionTestUtils.setField(message, "sendAttemptId", "attempt-1");
        ReflectionTestUtils.setField(message, "sendAttemptStartedAt", attemptStartedAt);
        ReflectionTestUtils.setField(message, "deliveryUncertainAt", LocalDateTime.of(2026, 4, 22, 13, 34));
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.recoverStaleProcessingMessages(
                LocalDateTime.now().minusMinutes(5),
                MessageQueuePolicy.MAX_RETRY_COUNT,
                recoveredAt);

        entityManager.flush();
        entityManager.clear();

        MessageQueue recovered = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo(MessageQueue.STATUS_DELIVERED_UNCONFIRMED);
        assertThat(recovered.getRetryCount()).isZero();
        assertThat(recovered.getProcessingStartedAt()).isNull();
        assertThat(recovered.getSendAttemptId()).isEqualTo("attempt-1");
        assertThat(recovered.getDeliveryUncertainAt()).isEqualTo(recoveredAt);
    }

    @Test
    @DisplayName("recoverStaleProcessingMessages marks message as failed when stale recovery reaches retry limit")
    void recoverStaleProcessingMessages_marksMessageAsFailedAtRetryLimit() {
        MessageQueue message = persistMessageQueue();
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "retryCount", MessageQueuePolicy.MAX_RETRY_COUNT - 1);
        ReflectionTestUtils.setField(message, "processingStartedAt", LocalDateTime.now().minusMinutes(10));
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.recoverStaleProcessingMessages(
                LocalDateTime.now().minusMinutes(5),
                MessageQueuePolicy.MAX_RETRY_COUNT,
                LocalDateTime.now());

        entityManager.flush();
        entityManager.clear();

        MessageQueue recovered = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo("FAILED");
        assertThat(recovered.getRetryCount()).isEqualTo(MessageQueuePolicy.MAX_RETRY_COUNT);
        assertThat(recovered.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("recoverStaleProcessingMessages also recovers legacy processing rows without lease timestamp")
    void recoverStaleProcessingMessages_recoversLegacyProcessingRowWithoutLeaseTimestamp() {
        MessageQueue message = persistMessageQueue();
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", null);
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.recoverStaleProcessingMessages(
                LocalDateTime.now().minusMinutes(5),
                MessageQueuePolicy.MAX_RETRY_COUNT,
                LocalDateTime.now());

        entityManager.flush();
        entityManager.clear();

        MessageQueue recovered = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo("PENDING");
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getProcessingStartedAt()).isNull();
    }

    @Test
    @DisplayName("pending queue lookup can be sorted by requestedAt and queueId")
    void findByStatusAndRetryCountLessThan_sortsByRequestedAtThenQueueId() {
        User user = persistUser();
        MessageQueue third = persistMessageQueue(
                user,
                "third",
                LocalDateTime.of(2026, 4, 22, 14, 0));
        MessageQueue first = persistMessageQueue(
                user,
                "first",
                LocalDateTime.of(2026, 4, 22, 13, 0));
        MessageQueue second = persistMessageQueue(
                user,
                "second",
                LocalDateTime.of(2026, 4, 22, 13, 0));

        entityManager.flush();
        entityManager.clear();

        List<MessageQueue> messages = messageQueueRepository.findByStatusAndRetryCountLessThan(
                "PENDING",
                MessageQueuePolicy.MAX_RETRY_COUNT,
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("queueId"))));

        assertThat(messages).extracting(MessageQueue::getQueueId)
                .containsExactly(first.getQueueId(), second.getQueueId(), third.getQueueId());
    }

    @Test
    @DisplayName("pending email queue id lookup excludes unsupported delivery methods")
    void findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod_filtersEmailMessages() {
        User user = persistUser();
        persistMessageQueue(
                user,
                "push",
                "PUSH",
                LocalDateTime.of(2026, 4, 22, 12, 0));
        MessageQueue email = persistMessageQueue(
                user,
                "email",
                "EMAIL",
                LocalDateTime.of(2026, 4, 22, 13, 0));

        entityManager.flush();
        entityManager.clear();

        List<Long> queueIds = messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                "PENDING",
                MessageQueuePolicy.MAX_RETRY_COUNT,
                "EMAIL",
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("queueId"))));

        assertThat(queueIds).containsExactly(email.getQueueId());
    }

    @Test
    @DisplayName("pending email queue id lookup keeps stable FIFO order")
    void findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod_sortsByRequestedAtThenQueueId() {
        User user = persistUser();
        MessageQueue third = persistMessageQueue(
                user,
                "third",
                "EMAIL",
                LocalDateTime.of(2026, 4, 22, 14, 0));
        MessageQueue first = persistMessageQueue(
                user,
                "first",
                "EMAIL",
                LocalDateTime.of(2026, 4, 22, 13, 0));
        MessageQueue second = persistMessageQueue(
                user,
                "second",
                "EMAIL",
                LocalDateTime.of(2026, 4, 22, 13, 0));

        entityManager.flush();
        entityManager.clear();

        List<Long> queueIds = messageQueueRepository.findPendingQueueIdsByStatusAndRetryCountLessThanAndDeliveryMethod(
                "PENDING",
                MessageQueuePolicy.MAX_RETRY_COUNT,
                "EMAIL",
                PageRequest.of(0, 10, Sort.by(Sort.Order.asc("requestedAt"), Sort.Order.asc("queueId"))));

        assertThat(queueIds).containsExactly(first.getQueueId(), second.getQueueId(), third.getQueueId());
    }

    @Test
    @DisplayName("claimed email dispatch projection returns scalar dispatch data only for processing email rows")
    void findEmailDispatchesByQueueIdIn_returnsProcessingEmailDispatchData() {
        User user = persistUser();
        MessageQueue email = persistMessageQueue(
                user,
                "email-content",
                "EMAIL",
                LocalDateTime.of(2026, 4, 22, 13, 0));
        MessageQueue pendingEmail = persistMessageQueue(
                user,
                "pending-email",
                "EMAIL",
                LocalDateTime.of(2026, 4, 22, 13, 1));
        MessageQueue push = persistMessageQueue(
                user,
                "push-content",
                "PUSH",
                LocalDateTime.of(2026, 4, 22, 13, 2));
        ReflectionTestUtils.setField(email, "status", MessageQueue.STATUS_PROCESSING);
        ReflectionTestUtils.setField(push, "status", MessageQueue.STATUS_PROCESSING);
        entityManager.flush();
        entityManager.clear();

        var dispatches = messageQueueRepository.findEmailDispatchesByQueueIdIn(
                List.of(email.getQueueId(), pendingEmail.getQueueId(), push.getQueueId()));

        assertThat(dispatches).hasSize(1);
        assertThat(dispatches.get(0).getQueueId()).isEqualTo(email.getQueueId());
        assertThat(dispatches.get(0).getTargetEmail()).isEqualTo("queue@test.com");
        assertThat(dispatches.get(0).getContent()).isEqualTo("email-content");
    }

    @Test
    @DisplayName("terminal queue cleanup deletes only old sent and failed rows within the batch limit")
    void deleteTerminalBatch_deletesOnlyExpiredTerminalRows() {
        User user = persistUser();
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 1, 0, 0);
        MessageQueue oldestSent = persistMessageQueue(user, "old-sent", cutoff.minusDays(40));
        MessageQueue oldFailed = persistMessageQueue(user, "old-failed", cutoff.minusDays(30));
        MessageQueue oldUnconfirmed = persistMessageQueue(user, "old-unconfirmed", cutoff.minusDays(50));
        MessageQueue recentSent = persistMessageQueue(user, "recent-sent", cutoff.minusDays(20));
        ReflectionTestUtils.setField(oldestSent, "status", MessageQueue.STATUS_SENT);
        ReflectionTestUtils.setField(oldFailed, "status", MessageQueue.STATUS_FAILED);
        ReflectionTestUtils.setField(oldUnconfirmed, "status", MessageQueue.STATUS_DELIVERED_UNCONFIRMED);
        ReflectionTestUtils.setField(recentSent, "status", MessageQueue.STATUS_SENT);
        entityManager.flush();

        updateModifiedAt(oldestSent, cutoff.minusDays(2));
        updateModifiedAt(oldFailed, cutoff.minusDays(1));
        updateModifiedAt(oldUnconfirmed, cutoff.minusDays(3));
        updateModifiedAt(recentSent, cutoff.plusDays(1));
        entityManager.clear();

        int deleted = messageQueueRepository.deleteTerminalBatch(cutoff, 1);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(messageQueueRepository.findById(oldestSent.getQueueId())).isEmpty();
        assertThat(messageQueueRepository.findById(oldFailed.getQueueId())).isPresent();
        assertThat(messageQueueRepository.findById(oldUnconfirmed.getQueueId())).isPresent();
        assertThat(messageQueueRepository.findById(recentSent.getQueueId())).isPresent();
    }

    @Test
    @DisplayName("delivered-unconfirmed cleanup deletes only rows past the investigation grace")
    void deleteDeliveredUnconfirmedBatch_deletesOnlyExpiredUnconfirmedRows() {
        User user = persistUser();
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 10, 0, 0);
        MessageQueue oldest = persistMessageQueue(user, "contains-private-email-content", cutoff.minusDays(10));
        MessageQueue second = persistMessageQueue(user, "second-private-content", cutoff.minusDays(9));
        MessageQueue recent = persistMessageQueue(user, "recent-private-content", cutoff.minusDays(8));
        MessageQueue sent = persistMessageQueue(user, "sent-content", cutoff.minusDays(20));
        markDeliveredUnconfirmed(oldest, cutoff.minusDays(3));
        markDeliveredUnconfirmed(second, cutoff.minusDays(2));
        markDeliveredUnconfirmed(recent, cutoff.plusHours(1));
        ReflectionTestUtils.setField(sent, "status", MessageQueue.STATUS_SENT);
        entityManager.flush();
        updateModifiedAt(oldest, cutoff.minusDays(3));
        updateModifiedAt(second, cutoff.minusDays(2));
        updateModifiedAt(recent, cutoff.minusDays(1));
        updateModifiedAt(sent, cutoff.minusDays(3));
        entityManager.clear();

        int deleted = messageQueueRepository.deleteDeliveredUnconfirmedBatch(cutoff, 1);
        entityManager.flush();
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(messageQueueRepository.findById(oldest.getQueueId())).isEmpty();
        assertThat(messageQueueRepository.findById(second.getQueueId())).isPresent();
        assertThat(messageQueueRepository.findById(recent.getQueueId())).isPresent();
        assertThat(messageQueueRepository.findById(sent.getQueueId())).isPresent();
    }

    private MessageQueue persistMessageQueue() {
        User user = persistUser();
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("content")
                .requestedAt(LocalDateTime.of(2026, 7, 7, 12, 0))
                .build();
        entityManager.persistAndFlush(message);
        return message;
    }

    private User persistUser() {
        User user = User.builder()
                .loginId("queue-user")
                .email("queue@test.com")
                .password("password")
                .displayName("Queue User")
                .build();
        entityManager.persist(user);
        return user;
    }

    private MessageQueue persistMessageQueue(User user, String content, LocalDateTime requestedAt) {
        return persistMessageQueue(user, content, "EMAIL", requestedAt);
    }

    private MessageQueue persistMessageQueue(User user, String content, String deliveryMethod, LocalDateTime requestedAt) {
        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod(deliveryMethod)
                .content(content)
                .requestedAt(requestedAt)
                .build();
        entityManager.persistAndFlush(message);
        return message;
    }

    private void updateModifiedAt(MessageQueue message, LocalDateTime modifiedAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE message_queue SET modified_at = :modifiedAt WHERE queue_id = :queueId")
                .setParameter("modifiedAt", modifiedAt)
                .setParameter("queueId", message.getQueueId())
                .executeUpdate();
    }

    private void markDeliveredUnconfirmed(MessageQueue message, LocalDateTime uncertainAt) {
        ReflectionTestUtils.setField(message, "status", MessageQueue.STATUS_DELIVERED_UNCONFIRMED);
        ReflectionTestUtils.setField(message, "deliveryUncertainAt", uncertainAt);
        ReflectionTestUtils.setField(message, "sendAttemptId", "attempt-" + message.getQueueId());
        ReflectionTestUtils.setField(message, "sendAttemptStartedAt", uncertainAt);
    }
}
