package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.badge.entity.Badge;
import com.weedrice.whiteboard.domain.badge.entity.UserBadge;
import com.weedrice.whiteboard.domain.badge.repository.BadgeRepository;
import com.weedrice.whiteboard.domain.badge.repository.UserBadgeRepository;
import com.weedrice.whiteboard.domain.notification.dto.NotificationEvent;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.Set;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeAwardServiceTest {

    @Mock BadgeRepository badges;
    @Mock UserBadgeRepository userBadges;
    @Mock UserRepository users;
    @Mock ApplicationEventPublisher events;
    BadgeAwardService service;

    @BeforeEach
    void setUp() {
        service = new BadgeAwardService(badges, userBadges, users, events,
                Clock.fixed(Instant.parse("2026-01-01T00:00:00Z"), ZoneOffset.UTC));
    }

    @Test
    void rejectsInvalidDuplicateAndInactiveRequests() {
        assertFalse(service.awardIfMissing(null, "CODE"));
        assertFalse(service.awardIfMissing(1L, " "));
        when(userBadges.existsByUser_UserIdAndBadge_BadgeCode(1L, "CODE")).thenReturn(true);
        assertFalse(service.awardIfMissing(1L, "CODE"));

        when(userBadges.existsByUser_UserIdAndBadge_BadgeCode(2L, "CODE")).thenReturn(false);
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(2L, User.STATUS_ACTIVE)).thenReturn(Optional.empty());
        assertFalse(service.awardIfMissing(2L, "CODE"));
    }

    @Test
    void awardsOnceAndPublishesNotification() {
        User user = mock(User.class);
        Badge badge = mock(Badge.class);
        UserBadge userBadge = UserBadge.builder().user(user).badge(badge).build();
        when(user.getUserId()).thenReturn(3L);
        when(badge.getBadgeCode()).thenReturn("CODE");
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(3L, User.STATUS_ACTIVE)).thenReturn(Optional.of(user));
        when(badges.findById("CODE")).thenReturn(Optional.of(badge));
        when(userBadges.insertIgnore(3L, "CODE", java.time.LocalDateTime.of(2026, 1, 1, 0, 0))).thenReturn(1);
        when(userBadges.findByUser_UserIdAndBadge_BadgeCode(3L, "CODE")).thenReturn(Optional.of(userBadge));

        assertTrue(service.awardIfMissing(3L, "CODE"));
        verify(events).publishEvent(any(NotificationEvent.class));
    }

    @Test
    void missingBadgeFailsAndConcurrentDuplicateIsIdempotent() {
        User user = mock(User.class);
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(4L, User.STATUS_ACTIVE)).thenReturn(Optional.of(user));
        when(badges.findById("MISSING")).thenReturn(Optional.empty());
        assertThrows(BusinessException.class, () -> service.awardIfMissing(4L, "MISSING"));

        Badge badge = mock(Badge.class);
        when(user.getUserId()).thenReturn(5L);
        when(badge.getBadgeCode()).thenReturn("RACE");
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(5L, User.STATUS_ACTIVE)).thenReturn(Optional.of(user));
        when(badges.findById("RACE")).thenReturn(Optional.of(badge));
        assertFalse(service.awardIfMissing(5L, "RACE"));
        verify(events, org.mockito.Mockito.never()).publishEvent(any());
    }

    @Test
    void batchPreloadsExistingAwardsAndDefinitionsBeforeInsertingMissingOnes() {
        User first = mock(User.class);
        User second = mock(User.class);
        Badge firstPost = mock(Badge.class);
        Badge postsTen = mock(Badge.class);
        Badge firstComment = mock(Badge.class);
        when(first.getUserId()).thenReturn(1L);
        when(second.getUserId()).thenReturn(2L);
        when(first.isActiveAccount()).thenReturn(true);
        when(second.isActiveAccount()).thenReturn(true);
        when(firstPost.getBadgeCode()).thenReturn(BadgeCode.FIRST_POST.name());
        when(postsTen.getBadgeCode()).thenReturn(BadgeCode.POSTS_10.name());
        when(firstComment.getBadgeCode()).thenReturn(BadgeCode.FIRST_COMMENT.name());
        UserBadge existing = UserBadge.builder().user(first).badge(firstPost).build();
        Set<String> requestedCodes = Set.of(
                BadgeCode.FIRST_POST.name(),
                BadgeCode.POSTS_10.name(),
                BadgeCode.FIRST_COMMENT.name());
        when(userBadges.findByUser_UserIdInAndBadge_BadgeCodeIn(Set.of(1L, 2L), requestedCodes))
                .thenReturn(List.of(existing));
        when(badges.findAllById(requestedCodes)).thenReturn(List.of(firstPost, postsTen, firstComment));
        when(userBadges.insertIgnore(any(), any(), any())).thenReturn(1);
        when(userBadges.findByUser_UserIdAndBadge_BadgeCode(1L, BadgeCode.POSTS_10.name()))
                .thenReturn(Optional.of(UserBadge.builder().user(first).badge(postsTen).build()));
        when(userBadges.findByUser_UserIdAndBadge_BadgeCode(2L, BadgeCode.FIRST_COMMENT.name()))
                .thenReturn(Optional.of(UserBadge.builder().user(second).badge(firstComment).build()));

        int awarded = service.awardMissingContentBadges(
                Map.of(1L, first, 2L, second),
                Map.of(
                        1L, Set.of(BadgeCode.FIRST_POST, BadgeCode.POSTS_10),
                        2L, Set.of(BadgeCode.FIRST_COMMENT)));

        assertEquals(2, awarded);
        verify(userBadges).findByUser_UserIdInAndBadge_BadgeCodeIn(Set.of(1L, 2L), requestedCodes);
        verify(badges).findAllById(requestedCodes);
        verify(userBadges, org.mockito.Mockito.times(2)).insertIgnore(any(), any(), any());
        verify(events, org.mockito.Mockito.times(2)).publishEvent(any(NotificationEvent.class));
        verifyNoInteractions(users);
    }
}
