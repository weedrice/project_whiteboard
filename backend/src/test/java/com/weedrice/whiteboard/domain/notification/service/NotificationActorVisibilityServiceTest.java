package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.notification.entity.Notification;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserBlockRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationActorVisibilityServiceTest {
    @Mock
    private UserBlockRepository userBlockRepository;

    @Test
    void resolveHiddenActorUserIds_queriesDistinctActivePageActorsOnly() {
        User blocked = actor(2L);
        User visible = actor(3L);
        User inactive = actor(4L);
        ReflectionTestUtils.setField(inactive, "status", "SUSPENDED");
        User deleted = actor(5L);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.of(2026, 9, 21, 10, 0));
        when(userBlockRepository.findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L, 3L)))
                .thenReturn(List.of(2L));

        var hidden = new NotificationActorVisibilityService(userBlockRepository).resolveHiddenActorUserIds(
                1L, List.of(notification(blocked), notification(visible), notification(blocked),
                        notification(inactive), notification(deleted), notification(null), notification(actor(null))));

        assertThat(hidden).containsExactlyInAnyOrder(2L, 4L, 5L);
        verify(userBlockRepository).findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L, 3L));
        verifyNoMoreInteractions(userBlockRepository);
    }

    @Test
    void resolveHiddenActorUserIds_emptyOrSystemOnlyPageSkipsBlockQuery() {
        var service = new NotificationActorVisibilityService(userBlockRepository);

        assertThat(service.resolveHiddenActorUserIds(1L, null)).isEmpty();
        assertThat(service.resolveHiddenActorUserIds(1L, List.of())).isEmpty();
        assertThat(service.resolveHiddenActorUserIds(
                1L, List.of(notification(null), notification(actor(null))))).isEmpty();

        verifyNoInteractions(userBlockRepository);
    }

    @Test
    void resolveHiddenActorUserIds_inactiveOnlyPageHidesActorsWithoutBlockQuery() {
        User inactive = actor(2L);
        ReflectionTestUtils.setField(inactive, "status", "SUSPENDED");
        User deleted = actor(3L);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.of(2026, 9, 21, 10, 0));

        var hidden = new NotificationActorVisibilityService(userBlockRepository).resolveHiddenActorUserIds(
                1L, List.of(notification(inactive), notification(deleted)));

        assertThat(hidden).containsExactlyInAnyOrder(2L, 3L);
        verifyNoInteractions(userBlockRepository);
    }

    private User actor(Long userId) {
        User user = User.builder().build();
        ReflectionTestUtils.setField(user, "userId", userId);
        return user;
    }

    private Notification notification(User actor) {
        return Notification.builder().actor(actor).build();
    }
}
