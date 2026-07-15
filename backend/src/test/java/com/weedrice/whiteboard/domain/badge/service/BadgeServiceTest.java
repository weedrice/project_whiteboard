package com.weedrice.whiteboard.domain.badge.service;

import com.weedrice.whiteboard.domain.badge.dto.BadgeBackfillResponse;
import com.weedrice.whiteboard.domain.badge.repository.BadgeRepository;
import com.weedrice.whiteboard.domain.badge.repository.UserBadgeRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.Pageable;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class BadgeServiceTest {

    @Mock BadgeRepository badges;
    @Mock UserBadgeRepository userBadges;
    @Mock UserRepository users;
    @Mock BadgeEvaluationService evaluations;
    BadgeService service;

    @BeforeEach
    void setUp() {
        service = new BadgeService(badges, userBadges, users, evaluations);
    }

    @Test
    void backfillEvaluatesActiveUsersAsOnePageBatch() {
        User active = mock(User.class);
        User inactive = mock(User.class);
        when(active.isActiveAccount()).thenReturn(true);
        when(inactive.isActiveAccount()).thenReturn(false);
        when(users.findAll(any(Pageable.class))).thenReturn(new PageImpl<>(List.of(active, inactive)));
        when(evaluations.evaluateContentCountBadges(any())).thenReturn(3);

        BadgeBackfillResponse response = service.backfillAll();

        assertEquals(1L, response.getScannedUsers());
        assertEquals(3L, response.getAwardedBadges());
        verify(evaluations).evaluateContentCountBadges(argThat(activeUsers -> activeUsers.equals(List.of(active))));
    }
}
