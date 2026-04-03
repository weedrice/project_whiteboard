package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettingsId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface UserNotificationSettingsRepository extends JpaRepository<UserNotificationSettings, UserNotificationSettingsId> {

    List<UserNotificationSettings> findByUserIdOrderByModifiedAtDescCreatedAtDesc(Long userId);

    Optional<UserNotificationSettings> findByUserIdAndNotificationType(Long userId, NotificationType notificationType);
}
