package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.constant.NotificationType;
import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.notification.repository.NotificationRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
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

import java.time.LocalDateTime;
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
    @Mock
    private UserBlockRepository userBlockRepository;

    private NotificationQueryService queryService;
    private Notification notification;

    @BeforeEach
    void setUp() {
        queryService = new NotificationQueryService(
                notificationRepository,
                notifications -> Map.of(),
                userBlockRepository);
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
    @DisplayName("Blocked notification actor is masked without changing page totals")
    void getNotifications_blockedActor_masksIdentityAndKeepsTotal() {
        User receiver = User.builder().build();
        ReflectionTestUtils.setField(receiver, "userId", 1L);
        User blockedActor = User.builder().displayName("Blocked Actor").build();
        ReflectionTestUtils.setField(blockedActor, "userId", 2L);
        Notification blockedNotification = Notification.builder()
                .user(receiver)
                .actor(blockedActor)
                .notificationType(NotificationType.LIKE)
                .sourceType("POST")
                .sourceId(10L)
                .content("Blocked Actor님이 게시글을 좋아합니다.")
                .messageKey("notification.post.liked")
                .messageParams("[\"Blocked Actor\"]")
                .build();
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(blockedNotification), PageRequest.of(0, 10), 17));
        when(userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(1L)).thenReturn(List.of(2L));

        var response = queryService.getNotifications(1L, PageRequest.of(0, 10));

        assertThat(response.getTotalElements()).isEqualTo(17);
        assertThat(response.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.getActor().getUserId()).isNull();
            assertThat(summary.getActor().getDisplayName()).isEmpty();
            assertThat(summary.getMessage()).doesNotContain("Blocked Actor");
            assertThat(summary.getMessageParams()).doesNotContain("Blocked Actor");
        });
    }

    @Test
    @DisplayName("Deleted notification actor is masked even without a block relation")
    void getNotifications_deletedActor_masksIdentity() {
        User receiver = User.builder().build();
        ReflectionTestUtils.setField(receiver, "userId", 1L);
        User deletedActor = User.builder().displayName("Deleted Actor").build();
        ReflectionTestUtils.setField(deletedActor, "userId", 2L);
        ReflectionTestUtils.setField(deletedActor, "deletedAt", LocalDateTime.now());
        Notification deletedActorNotification = Notification.builder()
                .user(receiver)
                .actor(deletedActor)
                .notificationType(NotificationType.LIKE)
                .sourceType("POST")
                .sourceId(10L)
                .content("Deleted Actor님이 게시글을 좋아합니다.")
                .build();
        when(notificationRepository.findByUser_UserIdOrderByCreatedAtDesc(eq(1L), any(Pageable.class)))
                .thenReturn(new PageImpl<>(List.of(deletedActorNotification)));
        when(userBlockRepository.findBlockedUserIdsEitherDirectionByUserId(1L)).thenReturn(List.of());

        var response = queryService.getNotifications(1L, PageRequest.of(0, 10));

        assertThat(response.getContent()).singleElement().satisfies(summary -> {
            assertThat(summary.getActor().getUserId()).isNull();
            assertThat(summary.getActor().getDisplayName()).isEmpty();
            assertThat(summary.getMessage()).doesNotContain("Deleted Actor");
        });
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
