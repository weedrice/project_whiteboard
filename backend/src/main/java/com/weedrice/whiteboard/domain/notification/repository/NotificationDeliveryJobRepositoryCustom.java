package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;

import java.time.LocalDateTime;

public interface NotificationDeliveryJobRepositoryCustom {
    int insertIfAbsent(NotificationDeliveryJob job, LocalDateTime now);

    int deleteCompletedBatch(LocalDateTime cutoff, int batchSize);

    int deleteFailedBatch(LocalDateTime cutoff, int batchSize);
}
