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

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class PushSubscriptionService {

    private final PushSubscriptionRepository pushSubscriptionRepository;
    private final UserWritableResolver userWritableResolver;
    private final UserSettingsService userSettingsService;

    @Transactional
    public PushSubscriptionResponse subscribe(Long userId, PushSubscriptionRequest request) {
        User user = userWritableResolver.resolve(userId);
        PushSubscription subscription = pushSubscriptionRepository.findByEndpoint(request.getEndpoint())
                .orElseGet(() -> PushSubscription.builder()
                        .user(user)
                        .endpoint(request.getEndpoint())
                        .p256dh(request.getKeys().getP256dh())
                        .auth(request.getKeys().getAuth())
                        .userAgent(request.getUserAgent())
                        .build());
        subscription.update(
                user,
                request.getKeys().getP256dh(),
                request.getKeys().getAuth(),
                request.getUserAgent());
        PushSubscription saved = pushSubscriptionRepository.save(subscription);
        userSettingsService.setPushEnabled(userId, true);
        return PushSubscriptionResponse.from(saved);
    }

    @Transactional
    public void unsubscribe(Long userId, PushSubscriptionRequest request) {
        userWritableResolver.resolve(userId);
        pushSubscriptionRepository.deleteByUser_UserIdAndEndpoint(userId, request.getEndpoint());
        userSettingsService.setPushEnabled(userId, pushSubscriptionRepository.existsByUser_UserId(userId));
    }
}
