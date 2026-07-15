package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Map;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationQueryServiceTest {

    @Mock
    private NotificationRepository notificationRepository;

    private NotificationQueryService queryService;
    private Notification notification;

    @BeforeEach
    void setUp() {
        queryService = new NotificationQueryService(notificationRepository, notifications -> Map.of());
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        notification = Notification.builder()
                .user(user)
                .notificationType(NotificationType.LIKE)
                .sourceType("POST")
                .sourceId(10L)
                .content("content")
                .build();
    }

    @Test
    @DisplayName("Notification query clamps pageable and keeps stable sort")
    void getNotifications_clampsPageable() {
        Pageable requestedPageable = PageRequest.of(0, 1000, Sort.by("unknown"));
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(notification)));

        queryService.getNotifications(1L, requestedPageable);

        ArgumentCaptor<Pageable> pageableCaptor = ArgumentCaptor.forClass(Pageable.class);
        verify(notificationRepository).findByUser_UserIdOrderByCreatedAtDesc(eq(1L), pageableCaptor.capture());
        assertThat(pageableCaptor.getValue().getPageSize()).isEqualTo(100);
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("createdAt")).isNotNull();
        assertThat(pageableCaptor.getValue().getSort().getOrderFor("notificationId")).isNotNull();
    }

    @Test
    @DisplayName("Unread notification count delegates to repository")
    void getUnreadNotificationCount_delegatesToRepository() {
        when(notificationRepository.countByUser_UserIdAndIsRead(1L, false)).thenReturn(3L);

        long count = queryService.getUnreadNotificationCount(1L);

        assertThat(count).isEqualTo(3L);
        verify(notificationRepository).countByUser_UserIdAndIsRead(1L, false);
    }
}
