package com.weedrice.whiteboard.domain.notification.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "keyword_notification_fanout_jobs",
        uniqueConstraints = @UniqueConstraint(name = "uk_keyword_notification_fanout_post", columnNames = "post_id"))
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class KeywordNotificationFanoutJob extends BaseTimeEntity {
    public enum Status { PENDING, PROCESSING, COMPLETED, FAILED }

    @Id @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "fanout_job_id")
    private Long fanoutJobId;
    @Column(name = "post_id", nullable = false, updatable = false)
    private Long postId;
    @Column(name = "last_subscription_id", nullable = false)
    private Long lastSubscriptionId;
    @Enumerated(EnumType.STRING) @Column(name = "status", nullable = false, length = 30)
    private Status status;
    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;
    @Column(name = "next_attempt_at", nullable = false)
    private LocalDateTime nextAttemptAt;
    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;
    @Column(name = "last_error", length = 160)
    private String lastError;

    public static KeywordNotificationFanoutJob pending(Long postId, LocalDateTime now) {
        KeywordNotificationFanoutJob job = new KeywordNotificationFanoutJob();
        job.postId = postId;
        job.lastSubscriptionId = 0L;
        job.status = Status.PENDING;
        job.retryCount = 0;
        job.nextAttemptAt = now;
        return job;
    }

    public boolean isDue(LocalDateTime now) { return status == Status.PENDING && !nextAttemptAt.isAfter(now); }
    public void claim(LocalDateTime now) { status = Status.PROCESSING; processingStartedAt = now; }
    public void advance(Long cursor, boolean completed, LocalDateTime now) {
        lastSubscriptionId = cursor;
        status = completed ? Status.COMPLETED : Status.PENDING;
        processingStartedAt = null;
        nextAttemptAt = now;
        lastError = null;
    }
    public void fail(String error, LocalDateTime retryAt) {
        retryCount++;
        status = retryCount >= 5 ? Status.FAILED : Status.PENDING;
        processingStartedAt = null;
        nextAttemptAt = retryAt;
        lastError = error == null ? null : error.substring(0, Math.min(160, error.length()));
    }
}
