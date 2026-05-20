package com.weedrice.whiteboard.domain.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class NotificationReadCommandServiceTest {

    @Mock
    private NotificationCommandService commandService;

    private NotificationReadCommandService readCommandService;

    @BeforeEach
    void setUp() {
        readCommandService = new NotificationReadCommandService(commandService);
    }

    @Test
    @DisplayName("Read notification delegates to command service")
    void readNotification_delegatesToCommandService() {
        readCommandService.readNotification(1L, 10L);

        verify(commandService).readNotification(1L, 10L);
    }

    @Test
    @DisplayName("Read all delegates to command service")
    void readAllNotifications_delegatesToCommandService() {
        readCommandService.readAllNotifications(1L);

        verify(commandService).readAllNotifications(1L);
    }
}
