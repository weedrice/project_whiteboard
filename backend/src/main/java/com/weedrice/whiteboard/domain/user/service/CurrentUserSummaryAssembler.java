package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.point.entity.UserPoint;
import com.weedrice.whiteboard.domain.point.repository.UserPointRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.entity.UserSettings;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class CurrentUserSummaryAssembler {

    private final UserPointRepository userPointRepository;
    private final UserSettingsRepository userSettingsRepository;

    public CurrentUserSummary assemble(User user) {
        return new CurrentUserSummary(
                user.getUserId(),
                user.getLoginId(),
                user.getEmail(),
                user.getDisplayName(),
                user.getProfileImageUrl(),
                user.getStatus(),
                resolveRole(user),
                resolveTheme(user.getUserId()),
                user.getIsEmailVerified(),
                user.getCreatedAt(),
                user.getLastLoginAt(),
                resolvePoints(user.getUserId()));
    }

    private String resolveRole(User user) {
        return Boolean.TRUE.equals(user.getIsSuperAdmin()) ? Role.SUPER_ADMIN : Role.USER;
    }

    private String resolveTheme(Long userId) {
        return userSettingsRepository.findById(userId)
                .map(UserSettings::getTheme)
                .map(this::normalizeTheme)
                .orElse(UserSettingsDefaults.THEME);
    }

    private String normalizeTheme(String theme) {
        if ("DARK".equalsIgnoreCase(theme)) {
            return "DARK";
        }
        return UserSettingsDefaults.THEME;
    }

    private Integer resolvePoints(Long userId) {
        return userPointRepository.findById(userId)
                .map(UserPoint::getCurrentPoint)
                .orElse(0);
    }

    public record CurrentUserSummary(
            Long userId,
            String loginId,
            String email,
            String displayName,
            String profileImageUrl,
            String status,
            String role,
            String theme,
            Boolean isEmailVerified,
            LocalDateTime createdAt,
            LocalDateTime lastLoginAt,
            Integer points) {
    }
}
