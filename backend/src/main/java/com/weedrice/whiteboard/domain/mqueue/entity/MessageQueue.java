package com.weedrice.whiteboard.domain.mqueue.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "message_queue", indexes = {
        @Index(name = "idx_message_queue_status", columnList = "status, requested_at"),
        @Index(name = "idx_message_queue_processing_started", columnList = "status, processing_started_at"),
        @Index(name = "idx_message_queue_user", columnList = "target_user_id")
})
public class MessageQueue extends BaseTimeEntity {
    public static final String STATUS_PENDING = "PENDING";
    public static final String STATUS_PROCESSING = "PROCESSING";
    public static final String STATUS_SENT = "SENT";
    public static final String STATUS_FAILED = "FAILED";
    public static final String STATUS_DELIVERED_UNCONFIRMED = "DELIVERED_UNCONFIRMED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "queue_id")
    private Long queueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(name = "delivery_method", length = 20, nullable = false)
    private String deliveryMethod; // EMAIL, PUSH, SMS

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "status", length = 50, nullable = false)
    private String status; // PENDING, PROCESSING, SENT, FAILED, DELIVERED_UNCONFIRMED

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Column(name = "processing_started_at")
    private LocalDateTime processingStartedAt;

    @Column(name = "send_attempt_id", length = 64)
    private String sendAttemptId;

    @Column(name = "send_attempt_started_at")
    private LocalDateTime sendAttemptStartedAt;

    @Column(name = "send_attempt_confirmed_at")
    private LocalDateTime sendAttemptConfirmedAt;

    @Column(name = "delivery_uncertain_at")
    private LocalDateTime deliveryUncertainAt;

    @Builder
    public MessageQueue(User targetUser, String deliveryMethod, String content) {
        this.targetUser = targetUser;
        this.deliveryMethod = deliveryMethod;
        this.content = content;
        this.requestedAt = LocalDateTime.now();
        this.status = STATUS_PENDING;
        this.retryCount = 0;
    }

    public void sent(LocalDateTime confirmedAt) {
        this.status = STATUS_SENT;
        this.processingStartedAt = null;
        this.sendAttemptConfirmedAt = confirmedAt;
        this.deliveryUncertainAt = null;
    }

    public void releaseProcessingLease() {
        this.status = STATUS_PENDING;
        this.processingStartedAt = null;
        clearSendAttempt();
    }

    public void failForRetry(int maxRetryCount) {
        this.processingStartedAt = null;
        clearSendAttempt();
        this.retryCount++;
        if (this.retryCount >= maxRetryCount) {
            this.status = STATUS_FAILED;
            return;
        }
        this.status = STATUS_PENDING;
    }

    private void clearSendAttempt() {
        this.sendAttemptId = null;
        this.sendAttemptStartedAt = null;
        this.sendAttemptConfirmedAt = null;
        this.deliveryUncertainAt = null;
    }
}
