package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationReadCommandServiceTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationCommandService commandService;

    private NotificationReadCommandService readCommandService;

    @BeforeEach
    void setUp() {
        readCommandService = new NotificationReadCommandService(userRepository, commandService);
    }

    @Test
    @DisplayName("Read notification delegates without user existence lookup")
    void readNotification_delegatesWithoutUserValidation() {
        readCommandService.readNotification(1L, 10L);

        verify(commandService).readNotification(1L, 10L);
        verify(userRepository, never()).existsById(1L);
    }

    @Test
    @DisplayName("Read all rejects missing user before bulk update")
    void readAllNotifications_missingUser_throwsUserNotFound() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> readCommandService.readAllNotifications(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
        verify(commandService, never()).readAllNotifications(1L);
    }
}
