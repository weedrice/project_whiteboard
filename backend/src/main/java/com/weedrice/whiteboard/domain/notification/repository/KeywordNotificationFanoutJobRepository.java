package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.*;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface KeywordNotificationFanoutJobRepository extends JpaRepository<KeywordNotificationFanoutJob, Long> {
    Optional<KeywordNotificationFanoutJob> findByPostId(Long postId);
    long countByStatus(KeywordNotificationFanoutJob.Status status);

    @Query("SELECT MIN(job.nextAttemptAt) FROM KeywordNotificationFanoutJob job WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob.Status.PENDING")
    Optional<LocalDateTime> findOldestPendingAttemptAt();

    @Modifying
    @Query(value = """
            INSERT INTO keyword_notification_fanout_jobs
                (post_id, last_subscription_id, status, retry_count, next_attempt_at, created_at, modified_at)
            VALUES (:postId, 0, 'PENDING', 0, :now, :now, :now)
            ON CONFLICT (post_id) DO NOTHING
            """, nativeQuery = true)
    int enqueueIfAbsent(@Param("postId") Long postId, @Param("now") LocalDateTime now);

    @Query("""
            SELECT new com.weedrice.whiteboard.domain.notification.repository.KeywordFanoutPage(
                    job.fanoutJobId, job.lastSubscriptionId, job.retryCount)
            FROM KeywordNotificationFanoutJob job
            WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob.Status.PENDING
              AND job.nextAttemptAt <= :now
            ORDER BY job.nextAttemptAt, job.fanoutJobId
            """)
    List<KeywordFanoutPage> findDuePages(@Param("now") LocalDateTime now, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT job FROM KeywordNotificationFanoutJob job
            WHERE job.fanoutJobId = :id AND job.lastSubscriptionId = :cursor AND job.retryCount = :retryCount
              AND job.status = com.weedrice.whiteboard.domain.notification.entity.KeywordNotificationFanoutJob.Status.PENDING
              AND job.nextAttemptAt <= :now
            """)
    Optional<KeywordNotificationFanoutJob> findDuePageForUpdate(
            @Param("id") Long id, @Param("cursor") Long cursor, @Param("retryCount") Integer retryCount,
            @Param("now") LocalDateTime now);
}
