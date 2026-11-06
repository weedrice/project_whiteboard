package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import com.weedrice.whiteboard.domain.notification.config.PushDeliveryJobProperties;
import com.weedrice.whiteboard.domain.notification.repository.PushDeliveryJobRepository;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;
import java.time.temporal.ChronoUnit;
import java.util.Optional;
import java.util.List;
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class PushDeliveryJobProcessorTest {

    private static final Long JOB_ID = 5L;

    @Mock PushDeliveryJobRepository jobRepository;
    @Mock PushDeliveryJobTransaction jobTransaction;
    @Mock PushDispatchSnapshotReader snapshotReader;
    @Mock PushNotificationDispatcher dispatcher;
    @Mock PushSubscriptionCleanupService subscriptionCleanupService;
    @Mock PushDeliveryJobMetrics metrics;
    @Mock PushPayloadFactory payloadFactory;
    @Mock UserBlockRepository userBlockRepository;
    @Mock ContentAuthorVisibilityRepository authorVisibilityRepository;
    @Mock NotificationPushDeliveryPolicy sourceDeliveryPolicy;

    private Clock clock;
    private LocalDateTime now;
    private PushSubscriptionSnapshot subscription;
    private PushDeliveryLease lease;
    private PushDeliveryJobProcessor processor;

    @BeforeEach
    void setUp() {
        clock = Clock.fixed(Instant.parse("2026-07-18T00:00:00.123456789Z"), ZoneOffset.UTC);
        now = LocalDateTime.ofInstant(clock.instant(), ZoneOffset.UTC).truncatedTo(ChronoUnit.MICROS);
        subscription = new PushSubscriptionSnapshot(
                9L, 2L, "https://push.example/endpoint", "key", "auth", now.minusMinutes(1));
        lease = new PushDeliveryLease(JOB_ID, now, subscription, "payload");
        processor = new PushDeliveryJobProcessor(
                jobRepository, jobTransaction, snapshotReader, dispatcher, subscriptionCleanupService,
                payloadFactory, userBlockRepository, authorVisibilityRepository, metrics, new PushDeliveryJobProperties(), clock, sourceDeliveryPolicy);
        when(jobTransaction.claim(JOB_ID, now)).thenReturn(lease);
        org.mockito.Mockito.lenient().when(sourceDeliveryPolicy.canDeliver(2L, "payload")).thenReturn(true);
        org.mockito.Mockito.lenient().when(payloadFactory.readActorUserId("payload")).thenReturn(null);
    }

    @Test
    void successCompletesClaimedJob() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.SUCCESS);
        when(jobTransaction.complete(lease)).thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(jobTransaction).complete(lease);
        verify(metrics).recordOutcome("success");
    }

    @Test
    void retryableFailureSchedulesBackoff() {
        PushDeliveryJob job = org.mockito.Mockito.mock(PushDeliveryJob.class);
        when(job.getRetryCount()).thenReturn(0);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.RETRYABLE_FAILURE);
        when(jobTransaction.retry(lease, "Retryable push response", now, now.plusMinutes(1)))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_RETRY);

        assertThat(processor.processJob(JOB_ID)).isFalse();

        verify(jobTransaction).retry(lease, "Retryable push response", now, now.plusMinutes(1));
        verify(metrics).recordOutcome("retry");
    }

    @Test
    void permanentFailureRecordsAppliedDeadLetter() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.PERMANENT_FAILURE);
        when(jobTransaction.failPermanently(lease, "Non-retryable push response", now))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_DEAD_LETTER);

        assertThat(processor.processJob(JOB_ID)).isFalse();

        verify(metrics).recordOutcome("dead_letter");
    }

    @Test
    void changedSubscriptionExpiresWithoutSending() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(false);
        when(jobTransaction.expire(lease, "Subscription changed or push disabled"))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(jobTransaction).expire(lease, "Subscription changed or push disabled");
        verify(metrics).recordOutcome("stale_subscription");
        org.mockito.Mockito.verifyNoInteractions(dispatcher);
    }

    @Test
    void blockedActorExpiresQueuedPushWithoutSending() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(payloadFactory.readActorUserId("payload")).thenReturn(7L);
        when(userBlockRepository.existsEitherDirection(2L, 7L)).thenReturn(true);
        when(jobTransaction.expire(lease, "Notification actor is blocked"))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(jobTransaction).expire(lease, "Notification actor is blocked");
        verify(metrics).recordOutcome("blocked_actor");
        org.mockito.Mockito.verifyNoInteractions(dispatcher);
    }

    @Test
    void newlyHiddenActorExpiresQueuedPushWithoutSendingStoredPayload() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(payloadFactory.readActorUserId("payload")).thenReturn(7L);
        when(authorVisibilityRepository.findVisibleUserIds(List.of(7L))).thenReturn(Set.of());
        when(jobTransaction.expire(lease, "Notification actor is not visible"))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(jobTransaction).expire(lease, "Notification actor is not visible");
        verify(metrics).recordOutcome("hidden_actor");
        org.mockito.Mockito.verifyNoInteractions(dispatcher);
    }

    @Test
    void eligibleActorIsDeliveredAfterVisibilityCheck() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(payloadFactory.readActorUserId("payload")).thenReturn(7L);
        when(authorVisibilityRepository.findVisibleUserIds(List.of(7L))).thenReturn(Set.of(7L));
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.SUCCESS);
        when(jobTransaction.complete(lease)).thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(authorVisibilityRepository).findVisibleUserIds(List.of(7L));
        verify(dispatcher).send(subscription, "payload");
    }

    @Test
    void invalidSnapshotIsTerminalizedWithoutDispatch() {
        PushDeliveryLease invalidLease = new PushDeliveryLease(
                JOB_ID, now, new PushSubscriptionSnapshot(9L, 2L, null, "key", "auth", now), "payload");
        when(jobTransaction.claim(JOB_ID, now)).thenReturn(invalidLease);
        when(jobTransaction.failPermanently(invalidLease, "Invalid push delivery snapshot", now))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_DEAD_LETTER);

        assertThat(processor.processJob(JOB_ID)).isFalse();

        verify(metrics).recordOutcome("invalid_payload");
        org.mockito.Mockito.verifyNoInteractions(dispatcher, snapshotReader);
    }

    @Test
    void expiredSubscriptionIsDeletedAfterJobTransition() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.EXPIRED);
        when(subscriptionCleanupService.expireDeliveryLease(lease, "Push provider expired subscription"))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);

        assertThat(processor.processJob(JOB_ID)).isTrue();

        verify(subscriptionCleanupService).expireDeliveryLease(lease, "Push provider expired subscription");
        verify(metrics).recordOutcome("expired");
    }

    @Test
    void successDoesNotReportSuccessWhenLeaseWasLost() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.SUCCESS);
        when(jobTransaction.complete(lease)).thenReturn(DeliveryJobTransitionResult.LEASE_LOST);

        assertThat(processor.processJob(JOB_ID)).isFalse();

        verify(metrics).recordOutcome("lease_lost");
        verify(metrics, never()).recordOutcome("success");
    }

    @Test
    void retryDoesNotReportRetryWhenLeaseWasLost() {
        PushDeliveryJob job = org.mockito.Mockito.mock(PushDeliveryJob.class);
        when(job.getRetryCount()).thenReturn(0);
        when(jobRepository.findById(JOB_ID)).thenReturn(Optional.of(job));
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.RETRYABLE_FAILURE);
        when(jobTransaction.retry(lease, "Retryable push response", now, now.plusMinutes(1)))
                .thenReturn(DeliveryJobTransitionResult.LEASE_LOST);

        assertThat(processor.processJob(JOB_ID)).isFalse();

        verify(metrics).recordOutcome("lease_lost");
        verify(metrics, never()).recordOutcome("retry");
    }
    @Test
    void hiddenKeywordSourceExpiresWithoutSendingOrRetrying() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(sourceDeliveryPolicy.canDeliver(2L, "payload")).thenReturn(false);
        when(jobTransaction.expire(lease, "Notification source is no longer visible"))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);
        assertThat(processor.processJob(JOB_ID)).isTrue();
        verify(metrics).recordOutcome("hidden_source");
        org.mockito.Mockito.verifyNoInteractions(dispatcher);
        verify(jobTransaction, never()).retry(org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any(),
                org.mockito.ArgumentMatchers.any(), org.mockito.ArgumentMatchers.any());
    }

    @Test
    void hiddenKeywordSourceWithLostLeaseDoesNotReportSuccess() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(sourceDeliveryPolicy.canDeliver(2L, "payload")).thenReturn(false);
        when(jobTransaction.expire(lease, "Notification source is no longer visible"))
                .thenReturn(DeliveryJobTransitionResult.LEASE_LOST);
        assertThat(processor.processJob(JOB_ID)).isFalse();
        verify(metrics).recordOutcome("lease_lost");
        verify(metrics, never()).recordOutcome("hidden_source");
        org.mockito.Mockito.verifyNoInteractions(dispatcher);
    }

    @Test
    void retryChecksSourceAgainBeforeNextProviderAttempt() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(sourceDeliveryPolicy.canDeliver(2L, "payload")).thenReturn(true).thenReturn(false);
        when(dispatcher.send(subscription, "payload")).thenReturn(PushDeliveryOutcome.RETRYABLE_FAILURE);
        when(jobTransaction.retry(lease, "Retryable push response", now, now.plusMinutes(1)))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_RETRY);
        when(jobTransaction.expire(lease, "Notification source is no longer visible"))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_SUCCESS);
        assertThat(processor.processJob(JOB_ID)).isFalse();
        assertThat(processor.processJob(JOB_ID)).isTrue();
        verify(dispatcher, org.mockito.Mockito.times(1)).send(subscription, "payload");
        verify(sourceDeliveryPolicy, org.mockito.Mockito.times(2)).canDeliver(2L, "payload");
        verify(metrics).recordOutcome("retry");
        verify(metrics).recordOutcome("hidden_source");
    }

    @Test
    void sourceLookupFailureRetriesInsteadOfSendingOrSilentlyDropping() {
        when(snapshotReader.isCurrentAndEnabled(subscription)).thenReturn(true);
        when(sourceDeliveryPolicy.canDeliver(2L, "payload")).thenThrow(new IllegalStateException("Unavailable"));
        when(jobTransaction.retry(lease, "IllegalStateException", now, now.plusMinutes(1)))
                .thenReturn(DeliveryJobTransitionResult.APPLIED_RETRY);
        assertThat(processor.processJob(JOB_ID)).isFalse();
        org.mockito.Mockito.verifyNoInteractions(dispatcher);
        verify(metrics).recordOutcome("retry");
    }

}
