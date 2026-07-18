package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.repository.RefreshTokenRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class RefreshTokenCleanupService {

    // Rotated/revoked rows remain available through expiry and an additional grace period for reuse detection.
    static final int REUSE_DETECTION_RETENTION_DAYS = 7;
    public static final int CLEANUP_BATCH_SIZE = 500;

    private final RefreshTokenRepository refreshTokenRepository;
    private final Clock clock;

    @Transactional
    public int deleteExpiredBatch() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(REUSE_DETECTION_RETENTION_DAYS);
        return refreshTokenRepository.deleteExpiredBatch(cutoff, CLEANUP_BATCH_SIZE);
    }
}
