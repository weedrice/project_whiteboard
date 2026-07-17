package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
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
import java.util.UUID;

public interface NotificationDeliveryJobRepository extends JpaRepository<NotificationDeliveryJob, Long> {

    Optional<NotificationDeliveryJob> findByEventId(UUID eventId);

    @Query("""
            SELECT job.jobId
            FROM NotificationDeliveryJob job
            WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob.Status.PENDING
              AND job.nextAttemptAt <= :now
            ORDER BY job.nextAttemptAt ASC, job.jobId ASC
            """)
    List<Long> findDueJobIds(@Param("now") LocalDateTime now, Pageable pageable);

    @Query("""
            SELECT job.jobId
            FROM NotificationDeliveryJob job
            WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob.Status.PROCESSING
              AND (job.processingStartedAt IS NULL OR job.processingStartedAt < :staleBefore)
            ORDER BY job.jobId ASC
            """)
    List<Long> findStaleProcessingJobIds(@Param("staleBefore") LocalDateTime staleBefore, Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT job FROM NotificationDeliveryJob job WHERE job.jobId = :jobId")
    Optional<NotificationDeliveryJob> findByIdForUpdate(@Param("jobId") Long jobId);

    long countByStatus(NotificationDeliveryJob.Status status);

    @Modifying
    @Query("""
            DELETE FROM NotificationDeliveryJob job
            WHERE job.status = com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob.Status.COMPLETED
              AND job.modifiedAt < :cutoff
            """)
    int deleteCompletedBefore(@Param("cutoff") LocalDateTime cutoff);
}
