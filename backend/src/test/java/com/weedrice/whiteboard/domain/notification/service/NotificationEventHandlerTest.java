package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private NotificationDeliveryJobService deliveryJobService;

    @Test
    void handleNotificationEvent_enqueuesDurableDeliveryJob() {
        NotificationEventHandler handler = new NotificationEventHandler(deliveryJobService);
        User receiver = User.builder().build();
        ReflectionTestUtils.setField(receiver, "userId", 1L);
        NotificationEvent event = new NotificationEvent(
                receiver,
                User.builder().build(),
                NotificationType.LIKE,
                NotificationSourceType.POST,
                10L,
                "content");

        handler.handleNotificationEvent(event);

        verify(deliveryJobService).enqueue(event);
    }
}
