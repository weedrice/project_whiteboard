package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.EnumSource;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class NotificationDeliveryJobRepositoryTest {

    @Autowired
    NotificationDeliveryJobRepository repository;

    @Autowired
    TestEntityManager entityManager;

    @Test
    void h2FallbackInsertIsIdempotentForSameEvent() {
        User receiver = User.builder()
                .loginId("delivery-receiver")
                .email("delivery-receiver@example.com")
                .password("password")
                .displayName("Delivery Receiver")
                .build();
        entityManager.persistAndFlush(receiver);
        NotificationEvent event = new NotificationEvent(
                receiver,
                null,
                NotificationType.SYSTEM,
                NotificationSourceType.SYSTEM,
                10L,
                "delivery");
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 0, 0);

        int inserted = repository.insertIfAbsent(NotificationDeliveryJob.from(event, now), now);
        int duplicate = repository.insertIfAbsent(NotificationDeliveryJob.from(event, now), now);

        assertThat(inserted).isEqualTo(1);
        assertThat(duplicate).isZero();
        assertThat(repository.findByEventId(event.getEventId())).isPresent();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void failedCleanupRechecksStatusAndKeepsRedrivenJob() {
        User receiver = User.builder()
                .loginId("delivery-cleanup-receiver")
                .email("delivery-cleanup-receiver@example.com")
                .password("password")
                .displayName("Delivery Cleanup Receiver")
                .build();
        entityManager.persistAndFlush(receiver);
        LocalDateTime failedAt = LocalDateTime.of(2026, 7, 1, 0, 0);
        NotificationDeliveryJob failed = failedJob(receiver, 20L, failedAt);
        NotificationDeliveryJob redriven = failedJob(receiver, 21L, failedAt);
        redriven.redrive(failedAt.plusDays(1));
        repository.saveAllAndFlush(java.util.List.of(failed, redriven));

        int deleted = repository.deleteFailedBatch(failedAt.plusDays(2), 10);
        entityManager.clear();

        assertThat(deleted).isEqualTo(1);
        assertThat(repository.findById(failed.getJobId())).isEmpty();
        assertThat(repository.findById(redriven.getJobId()))
                .get()
                .extracting(NotificationDeliveryJob::getStatus)
                .isEqualTo(NotificationDeliveryJob.Status.PENDING);
    }

    @Test
    void independentlyRecreatedKeywordEvents_reuseCompletedJobAndOriginalEnqueueTime() {
        User receiver = persistedReceiver();
        UUID eventId = UUID.nameUUIDFromBytes("keyword-post:10:receiver:1".getBytes(java.nio.charset.StandardCharsets.UTF_8));
        LocalDateTime now = LocalDateTime.of(2026, 7, 17, 0, 0);
        NotificationEvent firstEvent = NotificationEvent.localized(eventId, receiver, null, null,
                NotificationType.KEYWORD, NotificationSourceType.POST, 10L,
                "notification.keyword.matched", "vue3 release");
        NotificationDeliveryJob firstJob = NotificationDeliveryJob.from(firstEvent, now);
        firstJob.complete();
        assertThat(repository.insertIfAbsent(firstJob, now)).isEqualTo(1);
        entityManager.clear();

        NotificationEvent laterEvent = NotificationEvent.localized(eventId, receiver, null, null,
                NotificationType.KEYWORD, NotificationSourceType.POST, 10L,
                "notification.keyword.matched", "edited title");
        assertThat(repository.insertIfAbsent(NotificationDeliveryJob.from(laterEvent, now.plusHours(1)),
                now.plusHours(1))).isZero();

        NotificationDeliveryJob saved = repository.findByEventId(eventId).orElseThrow();
        assertThat(saved.getStatus()).isEqualTo(NotificationDeliveryJob.Status.COMPLETED);
        assertThat(saved.getCreatedAt()).isEqualTo(now);
        assertThat(saved.getContent()).isNull();
        assertThat(repository.count()).isEqualTo(1);
    }

    @Test
    void legacyKeywordLookup_returnsOldestMatchingPostAndReceiverOnly() {
        User receiver = persistedReceiver();
        User otherReceiver = entityManager.persistAndFlush(User.builder().loginId("another-receiver")
                .email("another-receiver@example.com").password("password").displayName("Other").build());
        LocalDateTime originalTime = LocalDateTime.of(2026, 7, 17, 0, 0);
        UUID newest = insertCompletedJob(receiver, NotificationType.KEYWORD, NotificationSourceType.POST, 10L,
                originalTime.plusHours(1));
        UUID oldest = insertCompletedJob(receiver, NotificationType.KEYWORD, NotificationSourceType.POST, 10L,
                originalTime);
        UUID sameTimeLaterId = insertCompletedJob(receiver, NotificationType.KEYWORD, NotificationSourceType.POST,
                10L, originalTime);
        insertCompletedJob(receiver, NotificationType.COMMENT, NotificationSourceType.POST, 10L,
                originalTime.minusDays(1));
        insertCompletedJob(receiver, NotificationType.KEYWORD, NotificationSourceType.COMMENT, 10L,
                originalTime.minusDays(1));
        insertCompletedJob(receiver, NotificationType.KEYWORD, NotificationSourceType.POST, 11L,
                originalTime.minusDays(1));
        insertCompletedJob(otherReceiver, NotificationType.KEYWORD, NotificationSourceType.POST, 10L,
                originalTime.minusDays(1));
        entityManager.clear();

        var matched = repository.findKeywordPostJobsForReceivers(10L, java.util.List.of(receiver.getUserId()));

        assertThat(matched).extracting(NotificationDeliveryJob::getEventId)
                .containsExactly(oldest, sameTimeLaterId, newest);
        assertThat(matched.getFirst().getCreatedAt()).isEqualTo(originalTime);
    }

    private UUID insertCompletedJob(User receiver, NotificationType type, NotificationSourceType sourceType,
                                    Long sourceId, LocalDateTime createdAt) {
        NotificationEvent event = NotificationEvent.localized(receiver, null, type, sourceType, sourceId,
                "notification.keyword.matched", "vue3 release");
        NotificationDeliveryJob job = NotificationDeliveryJob.from(event, createdAt);
        job.complete();
        assertThat(repository.insertIfAbsent(job, createdAt)).isEqualTo(1);
        return event.getEventId();
    }

    @ParameterizedTest
    @EnumSource(value = KeywordNotificationFanoutJob.Status.class, names = {"PENDING", "PROCESSING", "FAILED"})
    void terminalCleanup_keepsKeywordDedupUntilFanoutCompletes(KeywordNotificationFanoutJob.Status fanoutStatus) {
        User receiver = persistedReceiver();
        LocalDateTime oldTime = LocalDateTime.of(2026, 7, 1, 0, 0);
        KeywordNotificationFanoutJob fanout = KeywordNotificationFanoutJob.pending(10L, oldTime);
        ReflectionTestUtils.setField(fanout, "status", fanoutStatus);
        entityManager.persistAndFlush(fanout);
        insertTerminalJob(receiver, NotificationType.KEYWORD, true, oldTime);
        insertTerminalJob(receiver, NotificationType.KEYWORD, false, oldTime);
        insertTerminalJob(receiver, NotificationType.COMMENT, true, oldTime);
        insertTerminalJob(receiver, NotificationType.COMMENT, false, oldTime);

        assertThat(repository.deleteCompletedBatch(oldTime.plusDays(1), 10)).isEqualTo(1);
        assertThat(repository.deleteFailedBatch(oldTime.plusDays(1), 10)).isEqualTo(1);
        assertThat(repository.findAll()).extracting(NotificationDeliveryJob::getNotificationType)
                .containsExactly(NotificationType.KEYWORD, NotificationType.KEYWORD);

        fanout.advance(100L, true, oldTime.plusDays(2));
        entityManager.flush();
        assertThat(repository.deleteCompletedBatch(oldTime.plusDays(3), 10)).isEqualTo(1);
        assertThat(repository.deleteFailedBatch(oldTime.plusDays(3), 10)).isEqualTo(1);
        assertThat(repository.count()).isZero();
    }

    private void insertTerminalJob(User receiver, NotificationType type, boolean completed, LocalDateTime now) {
        NotificationEvent event = NotificationEvent.localized(receiver, null, type,
                NotificationSourceType.POST, 10L, "notification.keyword.matched", "vue3 release");
        NotificationDeliveryJob job = NotificationDeliveryJob.from(event, now);
        if (completed) {
            job.complete();
        } else {
            job.fail("failed", now, now, 1);
        }
        assertThat(repository.insertIfAbsent(job, now)).isEqualTo(1);
    }

    private User persistedReceiver() {
        User receiver = User.builder().loginId("keyword-receiver")
                .email("keyword-receiver@example.com").password("password").displayName("Receiver").build();
        return entityManager.persistAndFlush(receiver);
    }

    private NotificationDeliveryJob failedJob(User receiver, Long sourceId, LocalDateTime failedAt) {
        NotificationEvent event = new NotificationEvent(
                receiver,
                null,
                NotificationType.SYSTEM,
                NotificationSourceType.SYSTEM,
                sourceId,
                "delivery");
        NotificationDeliveryJob job = NotificationDeliveryJob.from(event, failedAt.minusDays(1));
        for (int retry = 0; retry < 5; retry++) {
            job.fail("failure", failedAt, failedAt.plusMinutes(1), 5);
        }
        return job;
    }
}
