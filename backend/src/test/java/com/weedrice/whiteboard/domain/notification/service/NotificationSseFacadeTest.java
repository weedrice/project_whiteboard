package com.weedrice.whiteboard.domain.notification.service;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.web.servlet.mvc.method.annotation.SseEmitter;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationSseFacadeTest {

    @Mock
    private NotificationStreamService streamService;

    private NotificationSseFacade sseFacade;

    @BeforeEach
    void setUp() {
        sseFacade = new NotificationSseFacade(streamService);
    }

    @Test
    @DisplayName("SSE subscribe delegates to stream subscription")
    void subscribe_delegatesToStreamService() {
        SseEmitter emitter = new SseEmitter();
        when(streamService.subscribe(1L)).thenReturn(emitter);

        assertThat(sseFacade.subscribe(1L)).isSameAs(emitter);
        verify(streamService).subscribe(1L);
    }
}
