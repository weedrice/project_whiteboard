package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.VerificationCodeRetentionProperties;
import com.weedrice.whiteboard.domain.auth.repository.VerificationCodeRepository;
import com.weedrice.whiteboard.domain.auth.repository.PasswordResetTokenRepository;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;
import java.util.concurrent.atomic.AtomicLong;

@Service
@RequiredArgsConstructor
public class VerificationCodeCleanupService {

    private final VerificationCodeRepository verificationCodeRepository;
    private final PasswordResetTokenRepository passwordResetTokenRepository;
    private final VerificationCodeRetentionProperties properties;
    private final Clock clock;
    private final MeterRegistry meterRegistry;
    private final AtomicLong pendingRecoveryBacklog = new AtomicLong();

    @PostConstruct
    void bindMetrics() {
        meterRegistry.gauge("noviis.verification.code.pending.recovery.backlog", pendingRecoveryBacklog);
    }

    @Transactional
    public int recoverStalePendingDeliveries() {
        LocalDateTime now = LocalDateTime.now(clock);
        LocalDateTime staleBefore = now.minusMinutes(properties.getPendingRecoveryGraceMinutes());
        int recovered = verificationCodeRepository.recoverStalePendingDeliveries(
                staleBefore,
                now,
                properties.getPendingRecoveryBatchSize());
        if (recovered > 0) {
            meterRegistry.counter("noviis.verification.code.pending.recovered").increment(recovered);
        }
        pendingRecoveryBacklog.set(verificationCodeRepository.countStalePendingDeliveries(staleBefore, now));
        return recovered;
    }

    @Transactional
    public int deleteExpiredTerminalBatch() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(properties.getTerminalRetentionDays());
        int deleted = verificationCodeRepository.deleteExpiredTerminalBatch(
                cutoff,
                properties.getCleanupBatchSize());
        if (deleted > 0) {
            meterRegistry.counter("noviis.verification.code.terminal.cleaned").increment(deleted);
        }
        return deleted;
    }

    @Transactional
    public int deleteExpiredPasswordResetTokenBatch() {
        LocalDateTime cutoff = LocalDateTime.now(clock)
                .minusDays(properties.getPasswordResetTokenRetentionDays());
        int deleted = passwordResetTokenRepository.deleteExpiredBatch(
                cutoff,
                properties.getPasswordResetTokenCleanupBatchSize());
        if (deleted > 0) {
            meterRegistry.counter("noviis.password.reset.token.cleaned").increment(deleted);
        }
        return deleted;
    }
}
