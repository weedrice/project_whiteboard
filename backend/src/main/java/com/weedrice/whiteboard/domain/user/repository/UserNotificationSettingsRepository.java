package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettingsId;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface UserNotificationSettingsRepository extends JpaRepository<UserNotificationSettings, UserNotificationSettingsId> {
    interface NotificationSettingReadProjection {
        Long getUserId();

        NotificationType getNotificationType();

        Boolean getEnabled();
    }

    List<UserNotificationSettings> findByUserIdOrderByModifiedAtDescCreatedAtDesc(Long userId);

    Optional<UserNotificationSettings> findByUserIdAndNotificationType(Long userId, NotificationType notificationType);

    @Query("""
            SELECT u.userId AS userId,
                   settings.notificationType AS notificationType,
                   settings.isEnabled AS enabled
            FROM User u
            LEFT JOIN UserNotificationSettings settings ON settings.user = u
            WHERE u.userId = :userId
            ORDER BY settings.modifiedAt DESC, settings.createdAt DESC
            """)
    List<NotificationSettingReadProjection> findNotificationSettingsReadByUserId(@Param("userId") Long userId);
}
