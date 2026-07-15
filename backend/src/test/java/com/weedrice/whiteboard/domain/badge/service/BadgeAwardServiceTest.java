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
import org.springframework.dao.DataIntegrityViolationException;

import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
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
        when(badge.getName()).thenReturn("First post");
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(3L, User.STATUS_ACTIVE)).thenReturn(Optional.of(user));
        when(badges.findById("CODE")).thenReturn(Optional.of(badge));
        when(userBadges.saveAndFlush(any(UserBadge.class))).thenAnswer(invocation -> invocation.getArgument(0));

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
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(5L, User.STATUS_ACTIVE)).thenReturn(Optional.of(user));
        when(badges.findById("RACE")).thenReturn(Optional.of(badge));
        when(userBadges.saveAndFlush(any(UserBadge.class))).thenThrow(new DataIntegrityViolationException("race"));
        assertFalse(service.awardIfMissing(5L, "RACE"));
        verify(events, never()).publishEvent(any());
    }
}
