package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;

@Service
@RequiredArgsConstructor
class PushSubscriptionCleanupService {

    private final PushSubscriptionRepository pushSubscriptionRepository;

    @Transactional
    public int deleteExpiredSubscriptions(Collection<Long> subscriptionIds) {
        if (subscriptionIds == null || subscriptionIds.isEmpty()) {
            return 0;
        }
        return pushSubscriptionRepository.deleteBySubscriptionIdIn(subscriptionIds);
    }
}
