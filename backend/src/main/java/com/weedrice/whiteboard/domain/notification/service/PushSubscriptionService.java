package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionResponse;
import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
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
    private final WebPushSubscriptionValidator subscriptionValidator;
    private final WebPushProperties webPushProperties;

    @Transactional
    public PushSubscriptionResponse subscribe(Long userId, PushSubscriptionRequest request) {
        String endpoint = request.getEndpoint();
        try {
            subscriptionValidator.validateForRegistration(
                    endpoint,
                    request.getKeys().getP256dh(),
                    request.getKeys().getAuth());
        } catch (InvalidWebPushSubscriptionException exception) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        pushSubscriptionRepository.lockEndpoint(endpoint);

        PushSubscription existingSubscription = pushSubscriptionRepository.findByEndpoint(endpoint).orElse(null);
        Long previousUserId = existingSubscription == null ? null : existingSubscription.getUser().getUserId();
        List<User> lockedUsers = userWritableResolver.resolveForUpdateWithRelatedUsers(
                userId,
                previousUserId == null || previousUserId.equals(userId) ? List.of() : List.of(previousUserId));
        User user = findLockedUser(lockedUsers, userId);

        boolean createsSubscriptionForCurrentUser = existingSubscription == null
                || !userId.equals(previousUserId);
        if (createsSubscriptionForCurrentUser
                && pushSubscriptionRepository.countByUser_UserId(userId) >= maxSubscriptionsPerUser()) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }

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

    @Transactional
    public int unsubscribeAll(Long userId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        int deleted = pushSubscriptionRepository.deleteAllByUserId(userId);
        pushSubscriptionRepository.flush();
        userSettingsService.setPushEnabledForLockedUser(user, false);
        return deleted;
    }

    private User findLockedUser(List<User> lockedUsers, Long userId) {
        return lockedUsers.stream()
                .filter(user -> userId.equals(user.getUserId()))
                .findFirst()
                .orElseThrow(() -> new IllegalStateException("Locked user not found"));
    }

    private int maxSubscriptionsPerUser() {
        int configuredLimit = webPushProperties.getMaxSubscriptionsPerUser();
        if (configuredLimit < 1) {
            throw new IllegalStateException("web-push.max-subscriptions-per-user must be positive");
        }
        return configuredLimit;
    }
}
