package com.weedrice.whiteboard.domain.feed.repository;

import com.weedrice.whiteboard.domain.feed.entity.FeedGenerationJob;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface FeedGenerationJobRepository extends JpaRepository<FeedGenerationJob, Long>,
        FeedGenerationJobRepositoryCustom {

    interface JobIdProjection {
        Long getJobId();
    }

    boolean existsByPostId(Long postId);

    @Query("""
            SELECT j.jobId AS jobId
            FROM FeedGenerationJob j
            WHERE j.status = :status
              AND j.retryCount < :retryCount
              AND (j.nextAttemptAt IS NULL OR j.nextAttemptAt <= :now)
            """)
    List<JobIdProjection> findJobIdsByStatusAndRetryCountLessThan(
            @Param("status") String status,
            @Param("retryCount") int retryCount,
            @Param("now") LocalDateTime now,
            Pageable pageable);

    long countByStatus(String status);

    @Query("""
            SELECT MIN(j.createdAt)
            FROM FeedGenerationJob j
            WHERE j.status = 'PENDING'
            """)
    Optional<LocalDateTime> findOldestPendingAt();

    Optional<FeedGenerationJob> findByJobIdAndStatusAndProcessingStartedAt(
            Long jobId,
            String status,
            LocalDateTime processingStartedAt);

    Optional<FeedGenerationJob> findByPostIdAndStatusAndProcessingStartedAt(
            Long postId,
            String status,
            LocalDateTime processingStartedAt);

    @Modifying
    @Transactional
    @Query("""
            update FeedGenerationJob j
            set j.status = 'PROCESSING',
                j.processingStartedAt = :claimedAt
            where j.jobId = :jobId
              and j.status = 'PENDING'
              and j.retryCount < :maxRetryCount
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :claimedAt)
            """)
    int claimForProcessing(
            @Param("jobId") Long jobId,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("claimedAt") LocalDateTime claimedAt);

    @Modifying
    @Transactional
    @Query("""
            update FeedGenerationJob j
            set j.status = 'PROCESSING',
                j.processingStartedAt = :claimedAt
            where j.postId = :postId
              and j.status = 'PENDING'
              and j.retryCount < :maxRetryCount
              and (j.nextAttemptAt is null or j.nextAttemptAt <= :claimedAt)
            """)
    int claimForProcessingByPostId(
            @Param("postId") Long postId,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("claimedAt") LocalDateTime claimedAt);

    @Modifying
    @Transactional
    @Query("""
            update FeedGenerationJob j
            set j.status = 'COMPLETED',
                j.processingStartedAt = null,
                j.completedAt = :completedAt,
                j.lastErrorMessage = null
            where j.jobId = :jobId
              and j.status = 'PROCESSING'
              and j.processingStartedAt = :expectedProcessingStartedAt
            """)
    int markCompleted(
            @Param("jobId") Long jobId,
            @Param("expectedProcessingStartedAt") LocalDateTime expectedProcessingStartedAt,
            @Param("completedAt") LocalDateTime completedAt);

    @Modifying
    @Transactional
    @Query("""
            update FeedGenerationJob j
            set j.retryCount = j.retryCount + 1,
                j.status = case
                    when (j.retryCount + 1) >= :maxRetryCount then 'FAILED'
                    else 'PENDING'
                end,
                j.processingStartedAt = null,
                j.lastErrorMessage = :lastErrorMessage,
                j.nextAttemptAt = :nextAttemptAt
            where j.jobId = :jobId
              and j.status = 'PROCESSING'
              and j.processingStartedAt = :expectedProcessingStartedAt
            """)
    int markFailedIfCurrent(
            @Param("jobId") Long jobId,
            @Param("expectedProcessingStartedAt") LocalDateTime expectedProcessingStartedAt,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastErrorMessage") String lastErrorMessage);

    @Modifying
    @Transactional
    @Query("""
            update FeedGenerationJob j
            set j.retryCount = j.retryCount + 1,
                j.status = case
                    when (j.retryCount + 1) >= :maxRetryCount then 'FAILED'
                    else 'PENDING'
                end,
                j.processingStartedAt = null,
                j.lastErrorMessage = :lastErrorMessage,
                j.nextAttemptAt = :nextAttemptAt
            where j.status = 'PROCESSING'
              and j.retryCount < :maxRetryCount
              and (j.processingStartedAt is null or j.processingStartedAt < :staleBefore)
            """)
    int recoverStaleProcessingJobs(
            @Param("staleBefore") LocalDateTime staleBefore,
            @Param("maxRetryCount") int maxRetryCount,
            @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
            @Param("lastErrorMessage") String lastErrorMessage);

    @Modifying
    @Transactional
    @Query("""
            update FeedGenerationJob j
            set j.status = 'PENDING',
                j.retryCount = 0,
                j.processingStartedAt = null,
                j.completedAt = null,
                j.lastErrorMessage = null,
                j.nextAttemptAt = :now
            where j.jobId = :jobId
              and j.status = 'FAILED'
            """)
    int redriveFailed(@Param("jobId") Long jobId, @Param("now") LocalDateTime now);
}
