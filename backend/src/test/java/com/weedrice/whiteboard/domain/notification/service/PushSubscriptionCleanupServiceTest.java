package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushSubscriptionCleanupServiceTest {

    @Test
    void deletesOnlySubscriptionsThatStillMatchDeliverySnapshots() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        UserWritableResolver userWritableResolver = mock(UserWritableResolver.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        PushSubscriptionCleanupService service = new PushSubscriptionCleanupService(
                repository, userWritableResolver, userSettingsService);
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 17, 12, 0);
        PushSubscriptionSnapshot stale = new PushSubscriptionSnapshot(
                1L, 10L, "https://push/stale", "old-key", "old-auth", modifiedAt);
        PushSubscriptionSnapshot unchanged = new PushSubscriptionSnapshot(
                2L, 10L, "https://push/expired", "key", "auth", modifiedAt);
        when(repository.deleteIfSnapshotMatches(
                1L, 10L, "https://push/stale", "old-key", "old-auth", modifiedAt)).thenReturn(0);
        when(repository.deleteIfSnapshotMatches(
                2L, 10L, "https://push/expired", "key", "auth", modifiedAt)).thenReturn(1);
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(10L);
        when(userWritableResolver.lockExistingUsersForUpdate(java.util.Set.of(10L))).thenReturn(List.of(user));
        when(repository.existsByUser_UserId(10L)).thenReturn(false);

        int deleted = service.deleteExpiredSubscriptions(List.of(stale, unchanged));

        assertThat(deleted).isEqualTo(1);
        var endpointLocks = inOrder(repository);
        endpointLocks.verify(repository).lockEndpoint("https://push/expired");
        endpointLocks.verify(repository).lockEndpoint("https://push/stale");
        verify(repository).deleteIfSnapshotMatches(
                1L, 10L, "https://push/stale", "old-key", "old-auth", modifiedAt);
        verify(repository).deleteIfSnapshotMatches(
                2L, 10L, "https://push/expired", "key", "auth", modifiedAt);
        verify(userSettingsService).setPushEnabledForLockedUser(user, false);
    }

    @Test
    void skipsSettingsUpdateWhenEveryDeliverySnapshotBecameStale() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        UserWritableResolver userWritableResolver = mock(UserWritableResolver.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        PushSubscriptionCleanupService service = new PushSubscriptionCleanupService(
                repository, userWritableResolver, userSettingsService);
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 17, 12, 0);
        PushSubscriptionSnapshot stale = new PushSubscriptionSnapshot(
                1L, 10L, "https://push/refreshed", "old-key", "old-auth", modifiedAt);
        User user = mock(User.class);
        when(user.getUserId()).thenReturn(10L);
        when(userWritableResolver.lockExistingUsersForUpdate(java.util.Set.of(10L))).thenReturn(List.of(user));

        int deleted = service.deleteExpiredSubscriptions(List.of(stale));

        assertThat(deleted).isZero();
        verifyNoInteractions(userSettingsService);
    }
}
