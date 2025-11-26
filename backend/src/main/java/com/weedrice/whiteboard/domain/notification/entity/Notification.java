package com.weedrice.whiteboard.domain.notification.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "notifications", indexes = {
        @Index(name = "idx_notifications_user", columnList = "user_id, is_read, created_at DESC")
})
public class Notification extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "notification_id")
    private Long notificationId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_id")
    private User actor;

    @Column(name = "notification_type", length = 50, nullable = false)
    private String notificationType; // COMMENT, LIKE, MENTION 등

    @Column(name = "source_type", length = 50, nullable = false)
    private String sourceType; // POST, COMMENT 등

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "content", length = 255, nullable = false)
    private String content;

    @Column(name = "is_read", length = 1, nullable = false)
    private String isRead;

    @Builder
    public Notification(User user, User actor, String notificationType, String sourceType, Long sourceId, String content) {
        this.user = user;
        this.actor = actor;
        this.notificationType = notificationType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.content = content;
        this.isRead = "N";
    }

    public void read() {
        this.isRead = "Y";
    }
}
