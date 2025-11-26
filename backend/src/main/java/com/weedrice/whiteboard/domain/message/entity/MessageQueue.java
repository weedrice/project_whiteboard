package com.weedrice.whiteboard.domain.message.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.BaseTimeEntity;
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
        @Index(name = "idx_message_queue_user", columnList = "target_user_id")
})
public class MessageQueue extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "queue_id")
    private Long queueId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(name = "delivery_method", length = 20, nullable = false)
    private String deliveryMethod; // EMAIL, PUSH, SMS

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "requested_at", nullable = false)
    private LocalDateTime requestedAt;

    @Column(name = "status", length = 50, nullable = false)
    private String status; // PENDING, SENT, FAILED

    @Column(name = "retry_count", nullable = false)
    private Integer retryCount;

    @Builder
    public MessageQueue(User targetUser, String deliveryMethod, String content) {
        this.targetUser = targetUser;
        this.deliveryMethod = deliveryMethod;
        this.content = content;
        this.requestedAt = LocalDateTime.now();
        this.status = "PENDING";
        this.retryCount = 0;
    }

    public void sent() {
        this.status = "SENT";
    }

    public void failed() {
        this.status = "FAILED";
        this.retryCount++;
    }
}
