package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.dto.PushSubscriptionRequest;
import com.weedrice.whiteboard.domain.notification.config.WebPushProperties;
import com.weedrice.whiteboard.domain.notification.entity.PushSubscription;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;
import java.util.Base64;
import java.util.HexFormat;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushSubscriptionServiceTest {

    @Mock PushSubscriptionRepository repository;
    @Mock PushDeliveryJobRepository jobs;
    @Mock UserWritableResolver userResolver;
    @Mock UserSettingsService settingsService;
    @Mock WebPushHostResolver hostResolver;
    PushSubscriptionService service;
    WebPushProperties properties;

    @BeforeEach
    void setUp() {
        properties = new WebPushProperties();
        properties.setAllowedHosts(List.of("push.example"));
        service = new PushSubscriptionService(
                repository,
                jobs,
                userResolver,
                settingsService,
                new WebPushSubscriptionValidator(properties, hostResolver),
                properties);
    }

    @Test
    void subscribeCreatesNewEndpointAndEnablesPush() {
        User user = mock(User.class);
        PushSubscriptionRequest request = request("https://push.example/new");
        when(user.getUserId()).thenReturn(7L);
        when(repository.findByEndpoint(request.getEndpoint())).thenReturn(Optional.empty());
        when(userResolver.resolveForUpdateWithRelatedUsers(7L, List.of())).thenReturn(List.of(user));
        when(repository.saveAndFlush(org.mockito.ArgumentMatchers.any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertEquals(request.getEndpoint(), service.subscribe(7L, request).getEndpoint());

        verify(repository).lockEndpoint(request.getEndpoint());
        verify(repository).saveAndFlush(org.mockito.ArgumentMatchers.argThat(subscription ->
                request.getEndpoint().equals(subscription.getEndpoint()) && subscription.getUser() == user));
        verify(settingsService).setPushEnabledForLockedUser(user, true);
    }

    @Test
    void subscribeMovesExistingEndpointAndDisablesPreviousUserWithoutAnotherSubscription() {
        User currentUser = mock(User.class);
        User previousUser = mock(User.class);
        PushSubscription existing = mock(PushSubscription.class);
        PushSubscriptionRequest request = request("https://push.example/existing");
        when(currentUser.getUserId()).thenReturn(8L);
        when(previousUser.getUserId()).thenReturn(7L);
        when(existing.getUser()).thenReturn(previousUser);
        when(repository.findByEndpoint(request.getEndpoint())).thenReturn(Optional.of(existing));
        when(userResolver.resolveForUpdateWithRelatedUsers(8L, List.of(7L)))
                .thenReturn(List.of(previousUser, currentUser));
        when(repository.saveAndFlush(existing)).thenReturn(existing);
        when(existing.getEndpoint()).thenReturn(request.getEndpoint());
        when(repository.existsByUser_UserId(7L)).thenReturn(false);

        service.subscribe(8L, request);

        verify(repository).lockEndpoint(request.getEndpoint());
        verify(existing).update(
                currentUser,
                request.getKeys().getP256dh(),
                request.getKeys().getAuth(),
                "browser");
        verify(settingsService).setPushEnabledForLockedUser(currentUser, true);
        verify(settingsService).setPushEnabledForLockedUser(previousUser, false);
    }

    @Test
    void subscribeKeepsPreviousUserEnabledWhenAnotherSubscriptionRemains() {
        User currentUser = mock(User.class);
        User previousUser = mock(User.class);
        PushSubscription existing = mock(PushSubscription.class);
        PushSubscriptionRequest request = request("https://push.example/transferred");
        when(currentUser.getUserId()).thenReturn(12L);
        when(previousUser.getUserId()).thenReturn(11L);
        when(existing.getUser()).thenReturn(previousUser);
        when(existing.getEndpoint()).thenReturn(request.getEndpoint());
        when(repository.findByEndpoint(request.getEndpoint())).thenReturn(Optional.of(existing));
        when(userResolver.resolveForUpdateWithRelatedUsers(12L, List.of(11L)))
                .thenReturn(List.of(previousUser, currentUser));
        when(repository.saveAndFlush(existing)).thenReturn(existing);
        when(repository.existsByUser_UserId(11L)).thenReturn(true);

        service.subscribe(12L, request);

        verify(settingsService).setPushEnabledForLockedUser(previousUser, true);
    }

    @Test
    void subscribeRejectsDisallowedEndpointBeforeTakingLocks() {
        PushSubscriptionRequest request = request("https://attacker.example/push");

        assertThatThrownBy(() -> service.subscribe(7L, request))
                .isInstanceOfSatisfying(BusinessException.class,
                        exception -> assertEquals(ErrorCode.VALIDATION_ERROR, exception.getErrorCode()));

        verify(repository, never()).lockEndpoint(request.getEndpoint());
        verify(userResolver, never()).resolveForUpdateWithRelatedUsers(
                org.mockito.ArgumentMatchers.anyLong(), org.mockito.ArgumentMatchers.anyList());
    }

    @Test
    void subscribeRejectsNewEndpointAtUserSubscriptionLimit() {
        properties.setMaxSubscriptionsPerUser(2);
        User user = mock(User.class);
        PushSubscriptionRequest request = request("https://push.example/third");
        when(user.getUserId()).thenReturn(7L);
        when(repository.findByEndpoint(request.getEndpoint())).thenReturn(Optional.empty());
        when(userResolver.resolveForUpdateWithRelatedUsers(7L, List.of())).thenReturn(List.of(user));
        when(repository.countByUser_UserId(7L)).thenReturn(2L);

        assertThatThrownBy(() -> service.subscribe(7L, request))
                .isInstanceOf(BusinessException.class);

        verify(repository, never()).saveAndFlush(org.mockito.ArgumentMatchers.any());
    }

    @Test
    void subscribeUpdatesOwnedEndpointEvenAtUserSubscriptionLimit() {
        properties.setMaxSubscriptionsPerUser(1);
        User user = mock(User.class);
        PushSubscription existing = mock(PushSubscription.class);
        PushSubscriptionRequest request = request("https://push.example/existing-owned");
        when(user.getUserId()).thenReturn(7L);
        when(existing.getUser()).thenReturn(user);
        when(existing.getEndpoint()).thenReturn(request.getEndpoint());
        when(repository.findByEndpoint(request.getEndpoint())).thenReturn(Optional.of(existing));
        when(userResolver.resolveForUpdateWithRelatedUsers(7L, List.of())).thenReturn(List.of(user));
        when(repository.saveAndFlush(existing)).thenReturn(existing);

        service.subscribe(7L, request);

        verify(repository, never()).countByUser_UserId(7L);
        verify(existing).update(user, request.getKeys().getP256dh(), request.getKeys().getAuth(), "browser");
    }

    @Test
    void unsubscribeReflectsWhetherAnotherSubscriptionRemains() {
        User user = mock(User.class);
        PushSubscriptionRequest request = request("https://push.example/remove");
        when(userResolver.resolveForUpdate(9L)).thenReturn(user);
        when(repository.existsByUser_UserId(9L)).thenReturn(true);

        service.unsubscribe(9L, request);

        verify(repository).lockEndpoint(request.getEndpoint());
        verify(userResolver).resolveForUpdate(9L);
        verify(repository).deleteByUser_UserIdAndEndpoint(9L, request.getEndpoint());
        verify(repository).flush();
        verify(settingsService).setPushEnabledForLockedUser(user, true);
        verify(settingsService, never()).setPushEnabledForLockedUser(user, false);
    }

    @Test
    void unsubscribeAllDeletesSubscriptionsAndDisablesPushUnderUserLock() {
        User user = mock(User.class);
        when(userResolver.resolveForUpdate(9L)).thenReturn(user);
        when(repository.deleteAllByUserId(9L)).thenReturn(3);

        assertEquals(3, service.unsubscribeAll(9L));

        var order = org.mockito.Mockito.inOrder(userResolver, repository, settingsService);
        order.verify(userResolver).resolveForUpdate(9L);
        verify(jobs).redactForReceiver(9L);
        order.verify(repository).deleteAllByUserId(9L);
        order.verify(repository).flush();
        order.verify(settingsService).setPushEnabledForLockedUser(user, false);
    }

    private static PushSubscriptionRequest request(String endpoint) {
        PushSubscriptionRequest.Keys keys = new PushSubscriptionRequest.Keys();
        byte[] publicKey = HexFormat.of().parseHex(
                "046b17d1f2e12c4247f8bce6e563a440f277037d812deb33a0f4a13945d898c296"
                        + "4fe342e2fe1a7f9b8ee7eb4a7c0f9e162bce33576b315ececbb6406837bf51f5");
        keys.setP256dh(Base64.getUrlEncoder().withoutPadding().encodeToString(publicKey));
        keys.setAuth(Base64.getUrlEncoder().withoutPadding().encodeToString(new byte[16]));
        PushSubscriptionRequest request = new PushSubscriptionRequest();
        request.setEndpoint(endpoint);
        request.setKeys(keys);
        request.setUserAgent("browser");
        return request;
    }
}
