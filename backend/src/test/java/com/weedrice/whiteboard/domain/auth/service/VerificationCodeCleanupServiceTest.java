package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.VerificationCodeRetentionProperties;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import org.junit.jupiter.api.Test;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneOffset;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

class VerificationCodeCleanupServiceTest {

    @Test
    void recoversExpiredPendingDeliveryAfterConfiguredGraceAndRecordsCount() {
        VerificationCodeRepository repository = mock(VerificationCodeRepository.class);
        PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        VerificationCodeRetentionProperties properties = new VerificationCodeRetentionProperties();
        properties.setPendingRecoveryGraceMinutes(30);
        properties.setPendingRecoveryBatchSize(100);
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T03:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        VerificationCodeCleanupService service = new VerificationCodeCleanupService(
                repository, passwordResetTokenRepository, properties, clock, meterRegistry);
        service.bindMetrics();
        LocalDateTime staleBefore = LocalDateTime.of(2026, 7, 17, 2, 30);
        LocalDateTime recoveredAt = LocalDateTime.of(2026, 7, 17, 3, 0);
        when(repository.recoverStalePendingDeliveries(staleBefore, recoveredAt, 100)).thenReturn(4);
        when(repository.countStalePendingDeliveries(staleBefore, recoveredAt)).thenReturn(6L);

        int recovered = service.recoverStalePendingDeliveries();

        assertThat(recovered).isEqualTo(4);
        verify(repository).recoverStalePendingDeliveries(staleBefore, recoveredAt, 100);
        verify(repository).countStalePendingDeliveries(staleBefore, recoveredAt);
        assertThat(meterRegistry.counter("noviis.verification.code.pending.recovered").count())
                .isEqualTo(4.0);
        assertThat(meterRegistry.get("noviis.verification.code.pending.recovery.backlog").gauge().value())
                .isEqualTo(6.0);
    }

    @Test
    void deletesConfiguredBatchBeforeRetentionCutoffAndRecordsCount() {
        VerificationCodeRepository repository = mock(VerificationCodeRepository.class);
        PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        VerificationCodeRetentionProperties properties = new VerificationCodeRetentionProperties();
        properties.setTerminalRetentionDays(7);
        properties.setCleanupBatchSize(200);
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T03:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        VerificationCodeCleanupService service = new VerificationCodeCleanupService(
                repository, passwordResetTokenRepository, properties, clock, meterRegistry);
        LocalDateTime cutoff = LocalDateTime.of(2026, 7, 10, 3, 0);
        when(repository.deleteExpiredTerminalBatch(cutoff, 200)).thenReturn(13);

        int deleted = service.deleteExpiredTerminalBatch();

        assertThat(deleted).isEqualTo(13);
        verify(repository).deleteExpiredTerminalBatch(cutoff, 200);
        assertThat(meterRegistry.counter("noviis.verification.code.terminal.cleaned").count())
                .isEqualTo(13.0);
    }

    @Test
    void deletesExpiredPasswordResetTokenBatchBeforeConfiguredCutoff() {
        VerificationCodeRepository repository = mock(VerificationCodeRepository.class);
        PasswordResetTokenRepository passwordResetTokenRepository = mock(PasswordResetTokenRepository.class);
        VerificationCodeRetentionProperties properties = new VerificationCodeRetentionProperties();
        properties.setPasswordResetTokenRetentionDays(30);
        properties.setPasswordResetTokenCleanupBatchSize(100);
        Clock clock = Clock.fixed(Instant.parse("2026-07-17T03:00:00Z"), ZoneOffset.UTC);
        SimpleMeterRegistry meterRegistry = new SimpleMeterRegistry();
        VerificationCodeCleanupService service = new VerificationCodeCleanupService(
                repository, passwordResetTokenRepository, properties, clock, meterRegistry);
        LocalDateTime cutoff = LocalDateTime.of(2026, 6, 17, 3, 0);
        when(passwordResetTokenRepository.deleteExpiredBatch(cutoff, 100)).thenReturn(9);

        assertThat(service.deleteExpiredPasswordResetTokenBatch()).isEqualTo(9);
        verify(passwordResetTokenRepository).deleteExpiredBatch(cutoff, 100);
        assertThat(meterRegistry.counter("noviis.password.reset.token.cleaned").count()).isEqualTo(9.0);
    }
}
