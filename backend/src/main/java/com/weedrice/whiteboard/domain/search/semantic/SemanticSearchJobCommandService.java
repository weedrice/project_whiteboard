package com.weedrice.whiteboard.domain.search.semantic;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Service
@RequiredArgsConstructor
class SemanticSearchJobCommandService {

    private final SemanticSearchJobRepository jobRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int recoverStaleProcessingJobs(LocalDateTime staleBefore, int maxRetryCount, String lastErrorMessage) {
        return jobRepository.recoverStaleProcessingJobs(staleBefore, maxRetryCount, lastErrorMessage);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int claimForProcessing(Long jobId, int maxRetryCount, LocalDateTime claimedAt) {
        return jobRepository.claimForProcessing(jobId, maxRetryCount, claimedAt);
    }

    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public Optional<SemanticSearchJob> findClaimed(Long jobId, LocalDateTime claimedAt) {
        return jobRepository.findClaimed(jobId, claimedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markCompleted(Long jobId, LocalDateTime expectedProcessingStartedAt, LocalDateTime completedAt) {
        return jobRepository.markCompleted(jobId, expectedProcessingStartedAt, completedAt);
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public int markFailedIfCurrent(Long jobId, LocalDateTime expectedProcessingStartedAt, int maxRetryCount,
            String lastErrorMessage) {
        return jobRepository.markFailedIfCurrent(jobId, expectedProcessingStartedAt, maxRetryCount, lastErrorMessage);
    }
}
