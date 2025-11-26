package com.weedrice.whiteboard.domain.message.repository;

import com.weedrice.whiteboard.domain.message.entity.MessageQueue;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface MessageQueueRepository extends JpaRepository<MessageQueue, Long> {
    List<MessageQueue> findByStatusAndRetryCountLessThan(String status, int retryCount);
}
