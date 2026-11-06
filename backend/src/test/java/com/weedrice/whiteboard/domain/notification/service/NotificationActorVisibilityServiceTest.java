package com.weedrice.whiteboard.domain.notification.service;

import com.weedrice.whiteboard.domain.actor.query.ContentAuthorVisibilityRepository;
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
import java.util.Set;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.verifyNoMoreInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class NotificationActorVisibilityServiceTest {
    @Mock
    private UserBlockRepository userBlockRepository;
    @Mock
    private ContentAuthorVisibilityRepository authorVisibilityRepository;

    @Test
    void resolveHiddenActorUserIds_queriesDistinctActivePageActorsOnly() {
        User blocked = actor(2L);
        User visible = actor(3L);
        User banned = actor(6L);
        User inactive = actor(4L);
        ReflectionTestUtils.setField(inactive, "status", "SUSPENDED");
        User deleted = actor(5L);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.of(2026, 9, 21, 10, 0));
        when(authorVisibilityRepository.findVisibleUserIds(Set.of(2L, 3L, 6L))).thenReturn(Set.of(2L, 3L));
        when(userBlockRepository.findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L, 3L, 6L)))
                .thenReturn(List.of(2L));

        var hidden = new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository).resolveHiddenActorUserIds(
                1L, List.of(notification(blocked), notification(visible), notification(banned), notification(blocked),
                        notification(inactive), notification(deleted), notification(null), notification(actor(null))));

        assertThat(hidden).containsExactlyInAnyOrder(2L, 4L, 5L, 6L);
        verify(authorVisibilityRepository).findVisibleUserIds(Set.of(2L, 3L, 6L));
        verify(userBlockRepository).findBlockedCandidateUserIdsEitherDirection(1L, List.of(2L, 3L, 6L));
        verifyNoMoreInteractions(userBlockRepository, authorVisibilityRepository);
    }

    @Test
    void resolveHiddenActorUserIds_emptyOrSystemOnlyPageSkipsBlockQuery() {
        var service = new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository);

        assertThat(service.resolveHiddenActorUserIds(1L, null)).isEmpty();
        assertThat(service.resolveHiddenActorUserIds(1L, List.of())).isEmpty();
        assertThat(service.resolveHiddenActorUserIds(
                1L, List.of(notification(null), notification(actor(null))))).isEmpty();

        verifyNoInteractions(userBlockRepository, authorVisibilityRepository);
    }

    @Test
    void resolveHiddenActorUserIds_inactiveOnlyPageHidesActorsWithoutBlockQuery() {
        User inactive = actor(2L);
        ReflectionTestUtils.setField(inactive, "status", "SUSPENDED");
        User deleted = actor(3L);
        ReflectionTestUtils.setField(deleted, "deletedAt", LocalDateTime.of(2026, 9, 21, 10, 0));

        var hidden = new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository).resolveHiddenActorUserIds(
                1L, List.of(notification(inactive), notification(deleted)));

        assertThat(hidden).containsExactlyInAnyOrder(2L, 3L);
        verifyNoInteractions(userBlockRepository, authorVisibilityRepository);
    }

    @Test
    void shouldHideActor_rechecksVisibilityForEveryDeliveryIncludingSelfActor() {
        var service = new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository);
        Notification event = notification(actor(2L));
        when(authorVisibilityRepository.findVisibleUserIds(List.of(2L)))
                .thenReturn(Set.of()).thenReturn(Set.of(2L));

        assertThat(service.shouldHideActor(2L, event)).isTrue();
        assertThat(service.shouldHideActor(2L, event)).isFalse();
        verifyNoInteractions(userBlockRepository);
    }

    @Test
    void shouldHideActor_systemNotificationSkipsEligibilityQueries() {
        var service = new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository);

        assertThat(service.shouldHideActor(1L, null)).isFalse();
        assertThat(service.shouldHideActor(1L, notification(null))).isFalse();
        verifyNoInteractions(userBlockRepository, authorVisibilityRepository);
    }

    @Test
    void withdrawnActorsUseContentVisibilityAndStillRespectBlocksAndBans() {
        User withdrawn = actor(2L);
        withdrawn.delete(LocalDateTime.now());
        var service = new NotificationActorVisibilityService(userBlockRepository, authorVisibilityRepository);
        when(authorVisibilityRepository.findVisibleUserIds(List.of(2L))).thenReturn(Set.of(2L));

        assertThat(service.shouldHideActor(1L, notification(withdrawn))).isFalse();
        when(userBlockRepository.existsEitherDirection(1L, 2L)).thenReturn(true);
        assertThat(service.shouldHideActor(1L, notification(withdrawn))).isTrue();
        when(authorVisibilityRepository.findVisibleUserIds(List.of(2L))).thenReturn(Set.of());
        assertThat(service.shouldHideActor(1L, notification(withdrawn))).isTrue();
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
