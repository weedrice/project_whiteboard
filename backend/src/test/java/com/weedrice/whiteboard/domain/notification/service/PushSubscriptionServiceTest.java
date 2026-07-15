package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock PushSubscriptionRepository repository;
    @Mock UserWritableResolver userResolver;
    @Mock UserSettingsService settingsService;
    PushSubscriptionService service;

    @BeforeEach
    void setUp() {
        service = new PushSubscriptionService(repository, userResolver, settingsService);
    }

    @Test
    void subscribeCreatesNewEndpointAndEnablesPush() {
        User user = mock(User.class);
        PushSubscriptionRequest request = request("https://push.example/new");
        when(userResolver.resolve(7L)).thenReturn(user);
        when(repository.findByEndpoint(request.getEndpoint())).thenReturn(Optional.empty());
        when(repository.save(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(request.getEndpoint(), service.subscribe(7L, request).getEndpoint());

        verify(repository).save(org.mockito.ArgumentMatchers.argThat(subscription ->
                request.getEndpoint().equals(subscription.getEndpoint()) && subscription.getUser() == user));
        verify(settingsService).setPushEnabled(7L, true);
    }

    @Test
    void subscribeMovesExistingEndpointToCurrentUser() {
        User user = mock(User.class);
        PushSubscription existing = mock(PushSubscription.class);
        PushSubscriptionRequest request = request("https://push.example/existing");
        when(userResolver.resolve(8L)).thenReturn(user);
        when(repository.findByEndpoint(request.getEndpoint())).thenReturn(Optional.of(existing));
        when(repository.save(existing)).thenReturn(existing);
        when(existing.getEndpoint()).thenReturn(request.getEndpoint());

        service.subscribe(8L, request);

        verify(existing).update(user, "key", "auth", "browser");
        verify(settingsService).setPushEnabled(8L, true);
    }

    @Test
    void unsubscribeReflectsWhetherAnotherSubscriptionRemains() {
        PushSubscriptionRequest request = request("https://push.example/remove");
        when(repository.existsByUser_UserId(9L)).thenReturn(true);

        service.unsubscribe(9L, request);

        verify(userResolver).resolve(9L);
        verify(repository).deleteByUser_UserIdAndEndpoint(9L, request.getEndpoint());
        verify(settingsService).setPushEnabled(9L, true);
        verify(settingsService, never()).setPushEnabled(9L, false);
    }

    private static PushSubscriptionRequest request(String endpoint) {
        PushSubscriptionRequest.Keys keys = new PushSubscriptionRequest.Keys();
        keys.setP256dh("key");
        keys.setAuth("auth");
        PushSubscriptionRequest request = new PushSubscriptionRequest();
        request.setEndpoint(endpoint);
        request.setKeys(keys);
        request.setUserAgent("browser");
        return request;
    }
}
