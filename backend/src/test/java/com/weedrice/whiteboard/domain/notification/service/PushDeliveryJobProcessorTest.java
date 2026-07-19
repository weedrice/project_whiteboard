package com.weedrice.whiteboard.domain.notification.service;

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
                payloadFactory, userBlockRepository, metrics, new PushDeliveryJobProperties(), clock);
        when(jobTransaction.claim(JOB_ID, now)).thenReturn(lease);
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
}
