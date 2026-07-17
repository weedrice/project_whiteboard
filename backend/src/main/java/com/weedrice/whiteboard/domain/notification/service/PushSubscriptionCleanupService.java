package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collection;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
class PushSubscriptionCleanupService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserWritableResolver userWritableResolver;
    private final UserSettingsService userSettingsService;

    @Transactional
    public int deleteExpiredSubscriptions(Collection<PushSubscriptionSnapshot> subscriptions) {
        if (subscriptions == null || subscriptions.isEmpty()) {
            return 0;
        }
        List<PushSubscriptionSnapshot> orderedSnapshots = subscriptions.stream()
                .distinct()
                .sorted(java.util.Comparator.comparing(PushSubscriptionSnapshot::endpoint)
                        .thenComparing(PushSubscriptionSnapshot::subscriptionId))
                .toList();
        orderedSnapshots.stream()
                .map(PushSubscriptionSnapshot::endpoint)
                .distinct()
                .forEach(pushSubscriptionRepository::lockEndpoint);

        Set<Long> snapshotUserIds = orderedSnapshots.stream()
                .map(PushSubscriptionSnapshot::userId)
                .collect(Collectors.toSet());
        Map<Long, User> lockedUsersById = userWritableResolver.lockExistingUsersForUpdate(snapshotUserIds).stream()
                .collect(Collectors.toMap(User::getUserId, Function.identity()));

        Set<Long> deletedUserIds = new HashSet<>();
        int deleted = 0;
        for (PushSubscriptionSnapshot subscription : orderedSnapshots) {
            int deletedSnapshot = pushSubscriptionRepository.deleteIfSnapshotMatches(
                    subscription.subscriptionId(),
                    subscription.userId(),
                    subscription.endpoint(),
                    subscription.p256dh(),
                    subscription.auth(),
                    subscription.modifiedAt());
            if (deletedSnapshot == 1) {
                deleted++;
                deletedUserIds.add(subscription.userId());
            }
        }

        deletedUserIds.stream().sorted().forEach(userId -> {
            User user = lockedUsersById.get(userId);
            if (user != null) {
                userSettingsService.setPushEnabledForLockedUser(
                        user,
                        pushSubscriptionRepository.existsByUser_UserId(userId));
            }
        });
        return deleted;
    }
}
