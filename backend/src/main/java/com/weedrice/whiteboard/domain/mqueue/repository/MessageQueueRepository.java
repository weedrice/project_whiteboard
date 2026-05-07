package com.weedrice.whiteboard.domain.mqueue.repository;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MessageQueueRepository extends JpaRepository<MessageQueue, Long> {
    List<MessageQueue> findByStatusAndRetryCountLessThan(String status, int retryCount);
    List<MessageQueue> findByStatusAndRetryCountLessThan(String status, int retryCount, Pageable pageable);
    List<MessageQueue> findByStatusAndRetryCountLessThanAndDeliveryMethod(
            String status,
            int retryCount,
            String deliveryMethod,
            Pageable pageable);

    @EntityGraph(attributePaths = "targetUser")
    @Query("select m from MessageQueue m where m.queueId = :queueId")
    Optional<MessageQueue> findByIdWithTargetUser(@Param("queueId") Long queueId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select m from MessageQueue m where m.queueId = :queueId")
    Optional<MessageQueue> findByIdForUpdate(@Param("queueId") Long queueId);

    @Modifying
    @Transactional
    @Query("""
            update MessageQueue m
            set m.status = 'PROCESSING',
                m.processingStartedAt = :claimedAt
            where m.queueId = :queueId
              and m.status = 'PENDING'
              and m.retryCount < :maxRetryCount
            """)
    int claimForProcessing(@Param("queueId") Long queueId,
                           @Param("maxRetryCount") int maxRetryCount,
                           @Param("claimedAt") LocalDateTime claimedAt);

    @Modifying
    @Transactional
    @Query("""
            update MessageQueue m
            set m.processingStartedAt = :renewedAt
            where m.queueId = :queueId
              and m.status = 'PROCESSING'
              and m.processingStartedAt = :expectedProcessingStartedAt
            """)
    int renewProcessingLeaseIfCurrent(@Param("queueId") Long queueId,
                                      @Param("expectedProcessingStartedAt") LocalDateTime expectedProcessingStartedAt,
                                      @Param("renewedAt") LocalDateTime renewedAt);

    @Modifying
    @Transactional
    @Query("""
            update MessageQueue m
            set m.retryCount = m.retryCount + 1,
                m.status = case when (m.retryCount + 1) >= :maxRetryCount then 'FAILED' else 'PENDING' end,
                m.processingStartedAt = null
            where m.status = 'PROCESSING'
              and (m.processingStartedAt is null or m.processingStartedAt < :staleBefore)
            """)
    int recoverStaleProcessingMessages(@Param("staleBefore") LocalDateTime staleBefore,
                                       @Param("maxRetryCount") int maxRetryCount);
}
