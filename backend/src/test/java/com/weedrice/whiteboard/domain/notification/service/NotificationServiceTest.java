package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationServiceTest {

    @Mock
    private NotificationRepository notificationRepository;
    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private NotificationService notificationService;

    private User user;
    private User actor;
    private Notification notification;

    @BeforeEach
    void setUp() {
        user = User.builder().build();
        actor = User.builder().build();
        notification = Notification.builder()
                .user(user)
                .actor(actor)
                .notificationType("LIKE")
                .sourceType("POST")
                .sourceId(1L)
                .content("Test Notification")
                .build();
    }

    @Test
    @DisplayName("알림 생성 성공")
    void createNotification_success() {
        // given
        Long userId = 1L;
        Long actorId = 2L;
        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(userRepository.findById(actorId)).thenReturn(Optional.of(actor));
        when(notificationRepository.save(any(Notification.class))).thenReturn(notification);

        // when
        notificationService.createNotification(userId, actorId, "LIKE", "POST", 1L, "Test Notification");

        // then
        verify(notificationRepository).save(any(Notification.class));
    }

    @Test
    @DisplayName("알림 읽음 처리 성공")
    void readNotification_success() {
        // given
        Long userId = 1L;
        Long notificationId = 1L;
        when(notificationRepository.findById(notificationId)).thenReturn(Optional.of(notification));

        // when
        notificationService.readNotification(userId, notificationId);

        // then
        assertThat(notification.getIsRead()).isEqualTo("Y");
    }
}
