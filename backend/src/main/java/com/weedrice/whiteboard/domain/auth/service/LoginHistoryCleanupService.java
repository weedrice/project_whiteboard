package com.weedrice.whiteboard.domain.auth.service;

import com.weedrice.whiteboard.domain.auth.LoginHistoryRetentionProperties;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import java.time.Clock;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class LoginHistoryCleanupService {

    private final LoginHistoryRepository loginHistoryRepository;
    private final LoginHistoryRetentionProperties properties;
    private final Clock clock;

    @Transactional
    public int deleteExpiredBatch() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusDays(properties.getRetentionDays());
        return loginHistoryRepository.deleteCreatedBeforeBatch(cutoff, properties.getCleanupBatchSize());
    }
}
