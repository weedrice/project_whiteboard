package com.weedrice.whiteboard.domain.mqueue.repository;

import com.weedrice.whiteboard.domain.mqueue.entity.MessageQueue;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

public interface MessageQueueRepository extends JpaRepository<MessageQueue, Long> {
    List<MessageQueue> findByStatusAndRetryCountLessThan(String status, int retryCount);
    List<MessageQueue> findByStatusAndRetryCountLessThan(String status, int retryCount, Pageable pageable);

    @Modifying
    @Transactional
    @Query("""
            update MessageQueue m
            set m.status = 'PROCESSING'
            where m.queueId = :queueId
              and m.status = 'PENDING'
              and m.retryCount < :maxRetryCount
            """)
    int claimForProcessing(@Param("queueId") Long queueId, @Param("maxRetryCount") int maxRetryCount);
}
