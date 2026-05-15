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
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSseFacadeTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private NotificationStreamService streamService;

    private NotificationSseFacade sseFacade;

    @BeforeEach
    void setUp() {
        sseFacade = new NotificationSseFacade(userRepository, streamService);
    }

    @Test
    @DisplayName("SSE subscribe validates user before stream subscription")
    void subscribe_validatesUser() {
        SseEmitter emitter = new SseEmitter();
        when(userRepository.existsById(1L)).thenReturn(true);
        when(streamService.subscribe(1L)).thenReturn(emitter);

        assertThat(sseFacade.subscribe(1L)).isSameAs(emitter);
        verify(streamService).subscribe(1L);
    }

    @Test
    @DisplayName("SSE subscribe rejects missing user")
    void subscribe_missingUser_throwsUserNotFound() {
        when(userRepository.existsById(1L)).thenReturn(false);

        assertThatThrownBy(() -> sseFacade.subscribe(1L))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }
}
