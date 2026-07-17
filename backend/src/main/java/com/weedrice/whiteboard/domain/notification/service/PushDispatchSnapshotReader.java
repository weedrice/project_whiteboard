package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.repository.UserSettingsRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Objects;

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
        return pushSubscriptionRepository.findDispatchableByUserId(userId).stream()
                .map(PushSubscriptionSnapshot::from)
                .toList();
    }

    @Transactional(readOnly = true)
    public boolean isCurrentAndEnabled(PushSubscriptionSnapshot expected) {
        if (expected == null || !isPushEnabled(expected.userId())) {
            return false;
        }
        return pushSubscriptionRepository.findById(expected.subscriptionId())
                .map(PushSubscriptionSnapshot::from)
                .filter(current -> Objects.equals(current.userId(), expected.userId()))
                .filter(current -> Objects.equals(current.endpoint(), expected.endpoint()))
                .filter(current -> Objects.equals(current.p256dh(), expected.p256dh()))
                .filter(current -> Objects.equals(current.auth(), expected.auth()))
                .filter(current -> Objects.equals(current.modifiedAt(), expected.modifiedAt()))
                .isPresent();
    }

    private boolean isPushEnabled(Long userId) {
        return userSettingsRepository.findSettingsReadByUserId(userId)
                .map(settings -> Boolean.TRUE.equals(settings.getPushEnabled()))
                .orElse(false);
    }
}
