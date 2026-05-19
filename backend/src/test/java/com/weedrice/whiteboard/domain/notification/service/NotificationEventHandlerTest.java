package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationEventHandlerTest {

    @Mock
    private NotificationCommandService commandService;

    @Mock
    private NotificationStreamService streamService;

    private NotificationEventHandler eventHandler;
    private User receiver;
    private Notification notification;

    @BeforeEach
    void setUp() {
        eventHandler = new NotificationEventHandler(commandService, streamService);
        receiver = User.builder().build();
        ReflectionTestUtils.setField(receiver, "userId", 1L);
        notification = Notification.builder()
                .user(receiver)
                .notificationType(NotificationType.LIKE)
                .sourceType("POST")
                .sourceId(10L)
                .content("content")
                .build();
        ReflectionTestUtils.setField(notification, "notificationId", 5L);
    }

    @Test
    @DisplayName("Notification event handler saves and delivers saved notification")
    void handleNotificationEvent_deliversSavedNotification() {
        NotificationEvent event = new NotificationEvent(
                receiver, User.builder().build(), NotificationType.LIKE, "POST", 10L, "content");
        when(commandService.handleNotificationEvent(event)).thenReturn(notification);

        eventHandler.handleNotificationEvent(event);

        verify(commandService).handleNotificationEvent(event);
        verify(streamService).deliverNotification(1L, notification);
    }
}
