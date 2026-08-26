package com.weedrice.whiteboard.domain.inquiry.integration;

import com.weedrice.whiteboard.domain.notification.constant.NotificationSourceType;
import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.notification.service.NotificationService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class InquiryNotificationAdapterTest {

    @Mock
    private NotificationService notificationService;

    @Mock
    private UserRepository userRepository;

    @Mock
    private GlobalConfigService globalConfigService;

    private InquiryNotificationAdapter adapter;
    private User author;
    private User actor;

    @BeforeEach
    void setUp() {
        adapter = new InquiryNotificationAdapter(notificationService, userRepository, globalConfigService);
        author = user(1L);
        actor = user(2L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(author));
        when(userRepository.findById(2L)).thenReturn(Optional.of(actor));
    }

    @Test
    @DisplayName("롤백 기간에는 기존 JAR이 읽을 수 있는 SYSTEM 타입으로 앱 알림을 발행한다")
    void notifyAuthor_rollbackWindow_usesSystemType() {
        when(globalConfigService.isInquiryNotificationTypeEnabled()).thenReturn(false);

        adapter.notifyAuthor(2L, 1L, 7L, "notification.inquiry.reply");

        NotificationEvent event = captureEvent();
        assertThat(event.getNotificationType()).isEqualTo(NotificationType.SYSTEM);
        assertThat(event.getSourceType()).isEqualTo(NotificationSourceType.INQUIRY);
        assertThat(event.getSourceId()).isEqualTo(7L);
        assertThat(event.isPushEnabled()).isFalse();
    }

    @Test
    @DisplayName("롤백 기간 종료 후에는 전용 INQUIRY 타입으로 앱 알림을 발행한다")
    void notifyAuthor_compatibilityGateEnabled_usesInquiryType() {
        when(globalConfigService.isInquiryNotificationTypeEnabled()).thenReturn(true);

        adapter.notifyAuthor(2L, 1L, 8L, "notification.inquiry.reply");

        NotificationEvent event = captureEvent();
        assertThat(event.getNotificationType()).isEqualTo(NotificationType.INQUIRY);
        assertThat(event.getSourceType()).isEqualTo(NotificationSourceType.INQUIRY);
        assertThat(event.isPushEnabled()).isFalse();
    }

    private NotificationEvent captureEvent() {
        ArgumentCaptor<NotificationEvent> captor = ArgumentCaptor.forClass(NotificationEvent.class);
        verify(notificationService).handleNotificationEvent(captor.capture());
        return captor.getValue();
    }

    private User user(Long userId) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }
}
