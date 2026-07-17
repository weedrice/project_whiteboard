package com.weedrice.whiteboard.domain.notification.entity;

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
@Table(name = "push_delivery_jobs",
        uniqueConstraints = @UniqueConstraint(
                name = "uk_push_delivery_jobs_event_subscription",
                columnNames = {"event_id", "subscription_id"}),
        indexes = {
                @Index(name = "idx_push_delivery_jobs_due", columnList = "status, next_attempt_at, job_id"),
                @Index(name = "idx_push_delivery_jobs_processing", columnList = "status, processing_started_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PushDeliveryJob extends BaseTimeEntity {

    public enum Status {
        PENDING,
        PROCESSING,
        COMPLETED,
        EXPIRED,
        FAILED
    }

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "job_id")
    private Long jobId;

    @Column(name = "event_id", nullable = false, updatable = false)
    private UUID eventId;

    @Column(name = "subscription_id", nullable = false, updatable = false)
    private Long subscriptionId;

    @Column(name = "receiver_user_id", nullable = false, updatable = false)
    private Long receiverUserId;

    @Column(name = "endpoint", length = 500, nullable = false, updatable = false)
    private String endpoint;

    @Column(name = "p256dh", length = 255, nullable = false, updatable = false)
    private String p256dh;

    @Column(name = "auth", length = 255, nullable = false, updatable = false)
    private String auth;

    @Column(name = "subscription_modified_at", nullable = false, updatable = false)
    private LocalDateTime subscriptionModifiedAt;

    @Column(name = "payload", columnDefinition = "TEXT", nullable = false, updatable = false)
    private String payload;

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

    @Column(name = "first_failed_at")
    private LocalDateTime firstFailedAt;

    @Column(name = "last_failed_at")
    private LocalDateTime lastFailedAt;

    public static PushDeliveryJob pending(
            UUID eventId,
            Long subscriptionId,
            Long receiverUserId,
            String endpoint,
            String p256dh,
            String auth,
            LocalDateTime subscriptionModifiedAt,
            String payload,
            LocalDateTime now) {
        PushDeliveryJob job = new PushDeliveryJob();
        job.eventId = eventId;
        job.subscriptionId = subscriptionId;
        job.receiverUserId = receiverUserId;
        job.endpoint = endpoint;
        job.p256dh = p256dh;
        job.auth = auth;
        job.subscriptionModifiedAt = subscriptionModifiedAt;
        job.payload = payload;
        job.status = Status.PENDING;
        job.retryCount = 0;
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

    public void expire(String reason) {
        status = Status.EXPIRED;
        processingStartedAt = null;
        lastError = truncate(reason);
    }

    public boolean retry(String error, LocalDateTime failedAt, LocalDateTime nextAttempt, int maxRetryCount) {
        recordFailure(error, failedAt);
        retryCount += 1;
        processingStartedAt = null;
        if (retryCount >= maxRetryCount) {
            status = Status.FAILED;
            return true;
        }
        status = Status.PENDING;
        nextAttemptAt = nextAttempt;
        return false;
    }

    public void failPermanently(String error, LocalDateTime failedAt) {
        recordFailure(error, failedAt);
        status = Status.FAILED;
        processingStartedAt = null;
    }

    private void recordFailure(String error, LocalDateTime failedAt) {
        lastError = truncate(error);
        firstFailedAt = firstFailedAt == null ? failedAt : firstFailedAt;
        lastFailedAt = failedAt;
    }

    private String truncate(String value) {
        if (value == null) {
            return null;
        }
        return value.length() <= 500 ? value : value.substring(0, 500);
    }
}
