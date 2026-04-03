package com.weedrice.whiteboard.domain.user.entity;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationTypeConverter;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "user_notification_settings")
@IdClass(UserNotificationSettingsId.class)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class UserNotificationSettings extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @Convert(converter = NotificationTypeConverter.class)
    @Column(name = "notification_type", length = 50)
    private NotificationType notificationType;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", insertable = false, updatable = false)
    private User user;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_enabled", nullable = false, length = 1)
    private Boolean isEnabled; // Y, N

    @Builder
    public UserNotificationSettings(Long userId, NotificationType notificationType, Boolean isEnabled) {
        this.userId = userId;
        this.notificationType = notificationType;
        this.isEnabled = isEnabled != null ? isEnabled : true;
    }

    public void setEnabled(boolean enabled) {
        this.isEnabled = enabled;
    }
}
