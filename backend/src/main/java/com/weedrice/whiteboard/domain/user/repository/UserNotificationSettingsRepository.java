package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettings;
import com.weedrice.whiteboard.domain.user.entity.UserNotificationSettingsId;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface UserNotificationSettingsRepository extends JpaRepository<UserNotificationSettings, UserNotificationSettingsId> {

    List<UserNotificationSettings> findByUserId(Long userId);
}