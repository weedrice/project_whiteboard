package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
class PushDispatchSnapshotReader {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserSettingsRepository userSettingsRepository;

    @Transactional(readOnly = true)
    public List<PushSubscriptionSnapshot> loadEnabledSubscriptions(Long userId) {
        if (userId == null || !isPushEnabled(userId)) {
            return List.of();
        }
        return pushSubscriptionRepository.findByUser_UserId(userId).stream()
                .map(PushSubscriptionSnapshot::from)
                .toList();
    }

    private boolean isPushEnabled(Long userId) {
        return userSettingsRepository.findSettingsReadByUserId(userId)
                .map(settings -> Boolean.TRUE.equals(settings.getPushEnabled()))
                .orElse(false);
    }
}
