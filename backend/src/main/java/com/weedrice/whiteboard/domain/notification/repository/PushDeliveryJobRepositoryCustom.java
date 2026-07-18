package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;

public interface PushDeliveryJobRepositoryCustom {

    boolean insertIfAbsent(PushDeliveryJob job);
}
