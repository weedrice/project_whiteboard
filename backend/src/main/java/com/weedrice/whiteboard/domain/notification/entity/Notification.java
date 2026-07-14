package com.weedrice.whiteboard.domain.notification.entity;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationTypeConverter;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.LocalDateTime;

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

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "actor_agent_id")
    private Agent actorAgent;

    @Convert(converter = NotificationTypeConverter.class)
    @JdbcTypeCode(SqlTypes.VARCHAR)
    @Column(name = "notification_type", length = 50, nullable = false)
    private NotificationType notificationType;

    @Column(name = "source_type", length = 50, nullable = false)
    private String sourceType;

    @Column(name = "source_id")
    private Long sourceId;

    @Column(name = "content", length = 255, nullable = false)
    private String content;

    @Column(name = "message_key", length = 160)
    private String messageKey;

    @Column(name = "message_params", columnDefinition = "TEXT")
    private String messageParams;

    @Column(name = "group_key", length = 160)
    private String groupKey;

    @Column(name = "group_count", nullable = false)
    private Integer groupCount;

    @Column(name = "last_event_at", nullable = false)
    private LocalDateTime lastEventAt;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_read", length = 1, nullable = false)
    private Boolean isRead;

    @Builder
    public Notification(User user, User actor, Agent actorAgent, NotificationType notificationType, String sourceType,
            Long sourceId, String content, String messageKey, String messageParams, String groupKey,
            LocalDateTime lastEventAt) {
        this.user = user;
        this.actor = actor;
        this.actorAgent = actorAgent;
        this.notificationType = notificationType;
        this.sourceType = sourceType;
        this.sourceId = sourceId;
        this.content = content;
        this.messageKey = messageKey;
        this.messageParams = messageParams;
        this.groupKey = groupKey;
        this.groupCount = 1;
        this.lastEventAt = lastEventAt != null ? lastEventAt : LocalDateTime.now();
        this.isRead = false;
    }

    public void merge(User actor, Agent actorAgent, String content, String messageKey, String messageParams,
            LocalDateTime eventAt) {
        this.actor = actor;
        this.actorAgent = actorAgent;
        this.content = content;
        this.messageKey = messageKey;
        this.messageParams = messageParams;
        this.groupCount = this.groupCount == null ? 2 : this.groupCount + 1;
        this.lastEventAt = eventAt != null ? eventAt : LocalDateTime.now();
    }

    public boolean isGrouped() {
        return groupCount != null && groupCount > 1;
    }

    public void read() {
        this.isRead = true;
    }
}
