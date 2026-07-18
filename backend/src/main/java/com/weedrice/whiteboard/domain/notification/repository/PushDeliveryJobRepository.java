package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface PushDeliveryJobRepository
        extends JpaRepository<PushDeliveryJob, Long>, PushDeliveryJobRepositoryCustom {

    @Query("""
            SELECT job.jobId FROM PushDeliveryJob job
            WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.PENDING
              AND job.nextAttemptAt <= :now
            ORDER BY job.nextAttemptAt ASC, job.jobId ASC
            """)
    List<Long> findDueJobIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            SELECT job.jobId FROM PushDeliveryJob job
            WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.PROCESSING
              AND (job.processingStartedAt IS NULL OR job.processingStartedAt < :staleBefore)
            ORDER BY job.jobId ASC
            """)
    List<Long> findStaleProcessingJobIds(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM PushDeliveryJob job WHERE job.jobId = :jobId")
    Optional<PushDeliveryJob> findByIdForUpdate(@Param("jobId") Long jobId);

    long countByStatus(PushDeliveryJob.Status status);

    @Query("SELECT MIN(job.nextAttemptAt) FROM PushDeliveryJob job WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.PENDING")
    Optional<LocalDateTime> findOldestPendingAttemptAt();

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PushDeliveryJob job
            SET job.status = CASE
                    WHEN job.status IN (
                        com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.PENDING,
                        com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.PROCESSING)
                    THEN com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.EXPIRED
                    ELSE job.status
                END,
                job.processingStartedAt = NULL,
                job.endpoint = NULL,
                job.p256dh = NULL,
                job.auth = NULL,
                job.payload = NULL
            WHERE job.receiverUserId = :userId
            """)
    int redactForReceiver(@Param("userId") Long userId);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE PushDeliveryJob job
            SET job.status = CASE
                    WHEN job.status IN (
                        com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.PENDING,
                        com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.PROCESSING)
                    THEN com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.EXPIRED
                    ELSE job.status
                END,
                job.processingStartedAt = NULL,
                job.endpoint = NULL,
                job.p256dh = NULL,
                job.auth = NULL,
                job.payload = NULL
            WHERE job.subscriptionId = :subscriptionId
              AND job.subscriptionModifiedAt = :modifiedAt
            """)
    int redactForSubscriptionSnapshot(
            @Param("subscriptionId") Long subscriptionId,
            @Param("modifiedAt") LocalDateTime modifiedAt);

    @Modifying
    @Query("""
            DELETE FROM PushDeliveryJob job
            WHERE job.status IN (
                com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.COMPLETED,
                com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.EXPIRED)
              AND job.modifiedAt < :cutoff
            """)
    int deleteTerminalBefore(@Param("cutoff") LocalDateTime cutoff);

    @Modifying
    @Query("""
            DELETE FROM PushDeliveryJob job
            WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob.Status.FAILED
              AND job.lastFailedAt < :cutoff
            """)
    int deleteFailedBefore(@Param("cutoff") LocalDateTime cutoff);
}
