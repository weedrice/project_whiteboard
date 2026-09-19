package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.notification.repository.PushSubscriptionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserSettingsService;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.inOrder;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

class PushSubscriptionCleanupServiceTest {

    @Test
    void expiredLeaseUsesEndpointUserJobLockOrderAndCanonicalizesSettings() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        PushDeliveryJobRepository jobs = mock(PushDeliveryJobRepository.class);
        UserWritableResolver userWritableResolver = mock(UserWritableResolver.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        PushSubscriptionCleanupService service = new PushSubscriptionCleanupService(
                repository, jobs, userWritableResolver, userSettingsService);
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 18, 12, 0);
        LocalDateTime claimedAt = modifiedAt.plusMinutes(1);
        PushSubscriptionSnapshot snapshot = new PushSubscriptionSnapshot(
                2L, 10L, "https://push/expired", "key", "auth", modifiedAt);
        PushDeliveryLease lease = new PushDeliveryLease(7L, claimedAt, snapshot, "payload");
        User user = mock(User.class);
        var job = mock(com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.class);
        when(userWritableResolver.lockExistingUsersForUpdate(java.util.Set.of(10L))).thenReturn(List.of(user));
        when(jobs.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        when(job.hasLease(claimedAt)).thenReturn(true);
        when(repository.deleteIfSnapshotMatches(
                2L, 10L, "https://push/expired", "key", "auth", modifiedAt)).thenReturn(1);
        when(repository.existsByUser_UserId(10L)).thenReturn(false);

        DeliveryJobTransitionResult result = service.expireDeliveryLease(lease, "expired");

        assertThat(result).isEqualTo(DeliveryJobTransitionResult.APPLIED_SUCCESS);
        var locks = inOrder(repository, userWritableResolver, jobs);
        locks.verify(repository).lockEndpoint("https://push/expired");
        locks.verify(userWritableResolver).lockExistingUsersForUpdate(java.util.Set.of(10L));
        locks.verify(jobs).findByIdForUpdate(7L);
        verify(job).expire("expired");
        verify(jobs).redactForSubscriptionSnapshot(2L, modifiedAt);
        verify(userSettingsService).setPushEnabledForLockedUser(user, false);
    }

    @Test
    void expiredLeaseDoesNotDeleteRefreshedSubscriptionOrChangeSettings() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        PushDeliveryJobRepository jobs = mock(PushDeliveryJobRepository.class);
        UserWritableResolver userWritableResolver = mock(UserWritableResolver.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        PushSubscriptionCleanupService service = new PushSubscriptionCleanupService(
                repository, jobs, userWritableResolver, userSettingsService);
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 17, 12, 0);
        LocalDateTime claimedAt = modifiedAt.plusMinutes(1);
        PushSubscriptionSnapshot stale = new PushSubscriptionSnapshot(
                1L, 10L, "https://push/refreshed", "old-key", "old-auth", modifiedAt);
        PushDeliveryLease lease = new PushDeliveryLease(7L, claimedAt, stale, "payload");
        User user = mock(User.class);
        var job = mock(com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.class);
        when(userWritableResolver.lockExistingUsersForUpdate(java.util.Set.of(10L))).thenReturn(List.of(user));
        when(jobs.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        when(job.hasLease(claimedAt)).thenReturn(true);
        when(repository.deleteIfSnapshotMatches(
                1L, 10L, "https://push/refreshed", "old-key", "old-auth", modifiedAt)).thenReturn(0);

        DeliveryJobTransitionResult result = service.expireDeliveryLease(lease, "expired");

        assertThat(result).isEqualTo(DeliveryJobTransitionResult.APPLIED_SUCCESS);
        verify(repository).deleteIfSnapshotMatches(
                1L, 10L, "https://push/refreshed", "old-key", "old-auth", modifiedAt);
        verify(job).expire("expired");
        verify(jobs, never()).redactForSubscriptionSnapshot(anyLong(), any());
        verify(repository, never()).existsByUser_UserId(anyLong());
        verifyNoInteractions(userSettingsService);
    }

    @Test
    void lostLeaseDoesNotDeleteSubscriptionOrChangeSettings() {
        PushSubscriptionRepository repository = mock(PushSubscriptionRepository.class);
        PushDeliveryJobRepository jobs = mock(PushDeliveryJobRepository.class);
        UserWritableResolver userWritableResolver = mock(UserWritableResolver.class);
        UserSettingsService userSettingsService = mock(UserSettingsService.class);
        PushSubscriptionCleanupService service = new PushSubscriptionCleanupService(
                repository, jobs, userWritableResolver, userSettingsService);
        LocalDateTime modifiedAt = LocalDateTime.of(2026, 7, 17, 12, 0);
        LocalDateTime claimedAt = modifiedAt.plusMinutes(1);
        PushSubscriptionSnapshot snapshot = new PushSubscriptionSnapshot(
                1L, 10L, "https://push/current", "key", "auth", modifiedAt);
        PushDeliveryLease lease = new PushDeliveryLease(7L, claimedAt, snapshot, "payload");
        User user = mock(User.class);
        var job = mock(com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.class);
        when(userWritableResolver.lockExistingUsersForUpdate(java.util.Set.of(10L))).thenReturn(List.of(user));
        when(jobs.findByIdForUpdate(7L)).thenReturn(Optional.of(job));
        when(job.hasLease(claimedAt)).thenReturn(false);

        DeliveryJobTransitionResult result = service.expireDeliveryLease(lease, "expired");

        assertThat(result).isEqualTo(DeliveryJobTransitionResult.LEASE_LOST);
        verify(repository, never()).deleteIfSnapshotMatches(
                anyLong(), anyLong(), anyString(), anyString(), anyString(), any());
        verify(job, never()).expire(anyString());
        verify(jobs, never()).redactForSubscriptionSnapshot(anyLong(), any());
        verifyNoInteractions(userSettingsService);
    }
}
