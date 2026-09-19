package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.annotation.Propagation;

import java.util.Set;

@Service
@RequiredArgsConstructor
class PushSubscriptionCleanupService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final PushDeliveryJobRepository pushDeliveryJobRepository;
    private final UserWritableResolver userWritableResolver;
    private final UserSettingsService userSettingsService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public DeliveryJobTransitionResult expireDeliveryLease(PushDeliveryLease lease, String reason) {
        PushSubscriptionSnapshot snapshot = lease.subscription();
        pushSubscriptionRepository.lockEndpoint(snapshot.endpoint());
        User lockedUser = userWritableResolver.lockExistingUsersForUpdate(Set.of(snapshot.userId())).stream()
                .findFirst()
                .orElse(null);
        var lockedJob = pushDeliveryJobRepository.findByIdForUpdate(lease.jobId())
                .filter(job -> job.hasLease(lease.claimedAt()))
                .orElse(null);
        if (lockedJob == null) {
            return DeliveryJobTransitionResult.LEASE_LOST;
        }

        int deleted = pushSubscriptionRepository.deleteIfSnapshotMatches(
                snapshot.subscriptionId(),
                snapshot.userId(),
                snapshot.endpoint(),
                snapshot.p256dh(),
                snapshot.auth(),
                snapshot.modifiedAt());
        lockedJob.expire(reason);
        if (deleted == 1) {
            pushDeliveryJobRepository.redactForSubscriptionSnapshot(
                    snapshot.subscriptionId(), snapshot.modifiedAt());
            if (lockedUser != null) {
                userSettingsService.setPushEnabledForLockedUser(
                        lockedUser,
                        pushSubscriptionRepository.existsByUser_UserId(snapshot.userId()));
            }
        }
        return DeliveryJobTransitionResult.APPLIED_SUCCESS;
    }

}
