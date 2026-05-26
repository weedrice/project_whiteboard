package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface UserSettingsRepository extends JpaRepository<UserSettings, Long> {
    interface SettingsReadProjection {
        Long getUserId();

        String getTheme();

        String getLanguage();

        String getTimezone();

        Boolean getHideNsfw();
    }

    @Query("""
            SELECT u.userId AS userId,
                   settings.theme AS theme,
                   settings.language AS language,
                   settings.timezone AS timezone,
                   settings.hideNsfw AS hideNsfw
            FROM User u
            LEFT JOIN UserSettings settings ON settings.user = u
            WHERE u.userId = :userId
            """)
    Optional<SettingsReadProjection> findSettingsReadByUserId(@Param("userId") Long userId);
}
