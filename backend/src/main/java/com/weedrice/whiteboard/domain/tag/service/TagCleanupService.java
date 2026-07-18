package com.weedrice.whiteboard.domain.tag.service;

import com.weedrice.whiteboard.domain.tag.config.TagCleanupProperties;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.TransientDataAccessException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
@Transactional(propagation = Propagation.NOT_SUPPORTED)
public class TagCleanupService {

    private static final long ORPHAN_TAG_TTL_HOURS = 24L;

    private final TagCleanupBatchWorker batchWorker;
    private final TagCleanupProperties properties;
    private final Clock clock;

    public int cleanupOrphanTags() {
        LocalDateTime cutoff = LocalDateTime.now(clock).minusHours(ORPHAN_TAG_TTL_HOURS);
        int totalDeleted = 0;
        for (int batch = 0; batch < properties.getMaxBatches(); batch++) {
            int deleted = deleteBatchWithRetry(cutoff);
            totalDeleted += deleted;
            if (deleted < properties.getBatchSize()) {
                break;
            }
        }
        return totalDeleted;
    }

    private int deleteBatchWithRetry(LocalDateTime cutoff) {
        for (int attempt = 1; attempt <= properties.getMaxRetryAttempts(); attempt++) {
            try {
                return batchWorker.deleteBatch(cutoff, properties.getBatchSize());
            } catch (TransientDataAccessException exception) {
                if (attempt == properties.getMaxRetryAttempts()) {
                    throw exception;
                }
                log.warn("Retrying orphan tag cleanup batch. attempt={}, exceptionType={}",
                        attempt, exception.getClass().getSimpleName());
            }
        }
        throw new IllegalStateException("Unreachable orphan tag cleanup retry state");
    }
}
