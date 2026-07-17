package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionResponse;
import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserWritableResolver userWritableResolver;
    private final UserSettingsService userSettingsService;

    @Transactional
    public PushSubscriptionResponse subscribe(Long userId, PushSubscriptionRequest request) {
        String endpoint = request.getEndpoint();
        pushSubscriptionRepository.lockEndpoint(endpoint);

        PushSubscription existingSubscription = pushSubscriptionRepository.findByEndpoint(endpoint).orElse(null);
        Long previousUserId = existingSubscription == null ? null : existingSubscription.getUser().getUserId();
        List<User> lockedUsers = userWritableResolver.resolveForUpdateWithRelatedUsers(
                userId,
                previousUserId == null || previousUserId.equals(userId) ? List.of() : List.of(previousUserId));
        User user = findLockedUser(lockedUsers, userId);

        PushSubscription subscription = existingSubscription == null
                ? PushSubscription.builder()
                        .user(user)
                        .endpoint(endpoint)
                        .p256dh(request.getKeys().getP256dh())
                        .auth(request.getKeys().getAuth())
                        .userAgent(request.getUserAgent())
                        .build()
                : existingSubscription;
        subscription.update(
                user,
                request.getKeys().getP256dh(),
                request.getKeys().getAuth(),
                request.getUserAgent());
        PushSubscription saved = pushSubscriptionRepository.saveAndFlush(subscription);
        userSettingsService.setPushEnabledForLockedUser(user, true);

        if (previousUserId != null && !previousUserId.equals(userId)) {
            User previousUser = findLockedUser(lockedUsers, previousUserId);
            userSettingsService.setPushEnabledForLockedUser(
                    previousUser,
                    pushSubscriptionRepository.existsByUser_UserId(previousUserId));
        }
        return PushSubscriptionResponse.from(saved);
    }

    @Transactional
    public void unsubscribe(Long userId, PushSubscriptionRequest request) {
        pushSubscriptionRepository.lockEndpoint(request.getEndpoint());
        User user = userWritableResolver.resolveForUpdate(userId);
        pushSubscriptionRepository.deleteByUser_UserIdAndEndpoint(userId, request.getEndpoint());
        pushSubscriptionRepository.flush();
        userSettingsService.setPushEnabledForLockedUser(
                user,
                pushSubscriptionRepository.existsByUser_UserId(userId));
    }

    private User findLockedUser(List<User> lockedUsers, Long userId) {
        return lockedUsers.stream()
                .filter(user -> userId.equals(user.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked user not found"));
    }
}
