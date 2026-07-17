package com.weedrice.whiteboard.domain.notification.entity;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.dto.NotificationMessageParamsCodec;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@Entity
@Table(name = "notification_delivery_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_notification_delivery_jobs_event",
                columnNames = "event_id"),
        indexes = {
                @Index(name = "idx_notification_delivery_jobs_pending",
                        columnList = "status, next_attempt_at, job_id"),
                @Index(name = "idx_notification_delivery_jobs_processing",
                        columnList = "status, processing_started_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class NotificationDeliveryJob extends BaseTimeEntity {

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "receiver_user_id", nullable = false, updatable = false)
    private Long receiverUserId;

    @Column(name = "actor_user_id", updatable = false)
    private Long actorUserId;

    @Column(name = "actor_agent_id", updatable = false)
    private Long actorAgentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "notification_type", length = 50, nullable = false, updatable = false)
    private NotificationType notificationType;

    @Enumerated(EnumType.STRING)
    @Column(name = "source_type", length = 50, nullable = false, updatable = false)
    private NotificationSourceType sourceType;

    @Column(name = "source_id", updatable = false)
    private Long sourceId;

    @Column(name = "content", columnDefinition = "TEXT", updatable = false)
    private String content;

    @Column(name = "message_key", length = 160, updatable = false)
    private String messageKey;

    @Column(name = "message_params", columnDefinition = "TEXT", updatable = false)
    private String messageParams;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", length = 30, nullable = false)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Column(name = "failure_code", length = 50)
    private String failureCode;

    @Column(name = "first_failed_at")
    private LocalDateTime firstFailedAt;

    @Column(name = "last_failed_at")
    private LocalDateTime lastFailedAt;

    @Column(name = "redrive_count", nullable = false)
    private Integer redriveCount;

    public static NotificationDeliveryJob from(NotificationEvent event, LocalDateTime now) {
        NotificationDeliveryJob job = new NotificationDeliveryJob();
        job.eventId = event.getEventId();
        job.receiverUserId = event.getUserToNotify().getUserId();
        job.actorUserId = event.getActor() == null ? null : event.getActor().getUserId();
        job.actorAgentId = event.getActorAgent() == null ? null : event.getActorAgent().getAgentId();
        job.notificationType = event.getNotificationType();
        job.sourceType = event.getSourceType();
        job.sourceId = event.getSourceId();
        job.content = event.getContent();
        job.messageKey = event.getMessageKey();
        job.messageParams = NotificationMessageParamsCodec.encode(event.getMessageParams());
        job.status = Status.PENDING;
        job.retryCount = 0;
        job.redriveCount = 0;
        job.nextAttemptAt = now;
        return job;
    }

    public boolean isDue(LocalDateTime now) {
        return status == Status.PENDING && !nextAttemptAt.isAfter(now);
    }

    public void claim(LocalDateTime claimedAt) {
        status = Status.PROCESSING;
        processingStartedAt = claimedAt;
        lastError = null;
    }

    public boolean hasLease(LocalDateTime claimedAt) {
        return status == Status.PROCESSING && claimedAt.equals(processingStartedAt);
    }

    public void complete() {
        status = Status.COMPLETED;
        processingStartedAt = null;
        lastError = null;
    }

    public boolean fail(String error, LocalDateTime failedAt, LocalDateTime nextAttempt, int maxRetryCount) {
        retryCount = retryCount + 1;
        processingStartedAt = null;
        lastError = truncate(error, 500);
        lastFailedAt = failedAt;
        if (firstFailedAt == null) {
            firstFailedAt = failedAt;
        }
        if (retryCount >= maxRetryCount) {
            status = Status.FAILED;
            return true;
        }
        status = Status.PENDING;
        nextAttemptAt = nextAttempt;
        return false;
    }

    public void rejectInvalidPayload(String reason, LocalDateTime failedAt) {
        status = Status.FAILED;
        processingStartedAt = null;
        failureCode = "INVALID_PAYLOAD";
        lastError = truncate(reason, 500);
        firstFailedAt = firstFailedAt == null ? failedAt : firstFailedAt;
        lastFailedAt = failedAt;
    }

    public boolean redrive(LocalDateTime now) {
        if (status != Status.FAILED) {
            return false;
        }
        status = Status.PENDING;
        retryCount = 0;
        redriveCount = redriveCount == null ? 1 : redriveCount + 1;
        nextAttemptAt = now;
        processingStartedAt = null;
        failureCode = null;
        lastError = null;
        return true;
    }

    private static String truncate(String value, int maxLength) {
        if (value == null) {
            return null;
        }
        return value.length() <= maxLength ? value : value.substring(0, maxLength);
    }
}
