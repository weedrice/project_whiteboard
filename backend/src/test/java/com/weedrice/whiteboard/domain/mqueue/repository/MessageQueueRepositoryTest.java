package com.weedrice.whiteboard.domain.mqueue.repository;

import com.weedrice.whiteboard.domain.mqueue.MessageQueuePolicy;
import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;

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
    @DisplayName("recoverStaleProcessingMessages increments retry count and releases stale processing lease")
    void recoverStaleProcessingMessages_requeuesStaleProcessingMessageWithinRetryBudget() {
        MessageQueue message = persistMessageQueue();
        ReflectionTestUtils.setField(message, "status", "PROCESSING");
        ReflectionTestUtils.setField(message, "processingStartedAt", LocalDateTime.now().minusMinutes(10));
        entityManager.persistAndFlush(message);

        int updated = messageQueueRepository.recoverStaleProcessingMessages(
                LocalDateTime.now().minusMinutes(5), MessageQueuePolicy.MAX_RETRY_COUNT);

        entityManager.flush();
        entityManager.clear();

        MessageQueue recovered = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo("PENDING");
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getProcessingStartedAt()).isNull();
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
                LocalDateTime.now().minusMinutes(5), MessageQueuePolicy.MAX_RETRY_COUNT);

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
                LocalDateTime.now().minusMinutes(5), MessageQueuePolicy.MAX_RETRY_COUNT);

        entityManager.flush();
        entityManager.clear();

        MessageQueue recovered = entityManager.find(MessageQueue.class, message.getQueueId());
        assertThat(updated).isEqualTo(1);
        assertThat(recovered.getStatus()).isEqualTo("PENDING");
        assertThat(recovered.getRetryCount()).isEqualTo(1);
        assertThat(recovered.getProcessingStartedAt()).isNull();
    }

    private MessageQueue persistMessageQueue() {
        User user = User.builder()
                .loginId("queue-user")
                .email("queue@test.com")
                .password("password")
                .displayName("Queue User")
                .build();
        entityManager.persist(user);

        MessageQueue message = MessageQueue.builder()
                .targetUser(user)
                .deliveryMethod("EMAIL")
                .content("content")
                .build();
        entityManager.persistAndFlush(message);
        return message;
    }
}
