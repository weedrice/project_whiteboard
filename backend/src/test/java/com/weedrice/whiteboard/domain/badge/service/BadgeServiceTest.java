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
import java.util.List;
import java.util.Optional;
import java.util.stream.IntStream;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
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
    void backfillUsesStableKeysetBatches() {
        List<User> firstBatch = usersFrom(1, 200);
        List<User> secondBatch = usersFrom(201, 400);
        List<User> lastBatch = usersFrom(401, 401);
        when(users.findTop200ByUserIdGreaterThanOrderByUserIdAsc(0L)).thenReturn(firstBatch);
        when(users.findTop200ByUserIdGreaterThanOrderByUserIdAsc(200L)).thenReturn(secondBatch);
        when(users.findTop200ByUserIdGreaterThanOrderByUserIdAsc(400L)).thenReturn(lastBatch);
        when(evaluations.evaluateContentCountBadges(firstBatch)).thenReturn(2);
        when(evaluations.evaluateContentCountBadges(secondBatch)).thenReturn(3);
        when(evaluations.evaluateContentCountBadges(lastBatch)).thenReturn(1);

        BadgeBackfillResponse response = service.backfillAll();

        assertEquals(401L, response.getScannedUsers());
        assertEquals(6L, response.getAwardedBadges());
        verify(users).findTop200ByUserIdGreaterThanOrderByUserIdAsc(0L);
        verify(users).findTop200ByUserIdGreaterThanOrderByUserIdAsc(200L);
        verify(users).findTop200ByUserIdGreaterThanOrderByUserIdAsc(400L);
    }

    @Test
    void publicBadgeLookupRejectsInactiveOrDeletedUser() {
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(7L, User.STATUS_ACTIVE)).thenReturn(Optional.empty());

        assertThrows(com.weedrice.whiteboard.global.exception.BusinessException.class,
                () -> service.getUserBadges(7L));

        verify(users).findByUserIdAndStatusAndDeletedAtIsNull(7L, User.STATUS_ACTIVE);
    }

    @Test
    void representativeBadgeLookupDoesNotExposeInactiveUser() {
        when(users.findByUserIdAndStatusAndDeletedAtIsNull(8L, User.STATUS_ACTIVE)).thenReturn(Optional.empty());

        assertNull(service.getRepresentativeBadge(8L));

        verify(users).findByUserIdAndStatusAndDeletedAtIsNull(8L, User.STATUS_ACTIVE);
    }

    private List<User> usersFrom(int firstId, int lastId) {
        return IntStream.rangeClosed(firstId, lastId)
                .mapToObj(id -> {
                    User user = mock(User.class);
                    if (id == lastId) {
                        when(user.getUserId()).thenReturn((long) id);
                    }
                    when(user.isActiveAccount()).thenReturn(true);
                    return user;
                })
                .toList();
    }
}
