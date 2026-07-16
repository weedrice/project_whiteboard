package com.weedrice.whiteboard.domain.attendance.service;

import com.weedrice.whiteboard.domain.attendance.entity.UserAttendance;
import com.weedrice.whiteboard.domain.attendance.repository.UserAttendanceRepository;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Clock;
import java.time.Instant;
import java.time.LocalDate;
import java.time.YearMonth;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AttendanceServiceTest {

    private static final LocalDate TODAY = LocalDate.of(2026, 1, 8);

    @Mock UserAttendanceRepository attendances;
    @Mock UserWritableResolver users;
    @Mock SanctionService sanctions;
    @Mock ContentRewardService rewards;
    @Mock BadgeEvaluationService badges;
    AttendanceService service;

    @BeforeEach
    void setUp() {
        service = new AttendanceService(attendances, users, sanctions, rewards,
                Clock.fixed(Instant.parse("2026-01-08T00:00:00Z"), ZoneOffset.UTC), badges);
    }

    @Test
    void duplicateCheckInReturnsExistingWithoutReward() {
        User user = mock(User.class);
        UserAttendance existing = new UserAttendance(user, TODAY, 3);
        when(users.resolveForUpdate(1L)).thenReturn(user);
        when(attendances.findByUser_UserIdAndAttendanceDate(1L, TODAY)).thenReturn(Optional.of(existing));

        var response = service.checkIn(1L);

        assertTrue(response.isAlreadyCheckedIn());
        assertEquals(0, response.getEarnedPoints());
        verify(sanctions).validateNotBanned(user);
    }

    @Test
    void seventhConsecutiveCheckInAwardsBaseBonusAndBadge() {
        User user = mock(User.class);
        UserAttendance previous = new UserAttendance(user, TODAY.minusDays(1), 6);
        when(users.resolveForUpdate(2L)).thenReturn(user);
        when(attendances.findByUser_UserIdAndAttendanceDate(2L, TODAY)).thenReturn(Optional.empty());
        when(attendances.findTopByUser_UserIdAndAttendanceDateBeforeOrderByAttendanceDateDesc(2L, TODAY))
                .thenReturn(Optional.of(previous));
        when(attendances.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));
        when(rewards.rewardCreate(2L, null, ContentRewardPolicy.ATTENDANCE)).thenReturn(10);
        when(rewards.rewardCreate(2L, null, ContentRewardPolicy.ATTENDANCE_STREAK_7)).thenReturn(20);

        var response = service.checkIn(2L);

        assertFalse(response.isAlreadyCheckedIn());
        assertEquals(7, response.getStreakCount());
        assertEquals(30, response.getEarnedPoints());
        verify(badges).evaluateAttendanceStreakBadges(2L, 7);
    }

    @Test
    void checkInUsesUserWriteLockBeforeInsert() {
        User user = mock(User.class);
        when(users.resolveForUpdate(3L)).thenReturn(user);
        when(attendances.findByUser_UserIdAndAttendanceDate(3L, TODAY)).thenReturn(Optional.empty());
        when(attendances.findTopByUser_UserIdAndAttendanceDateBeforeOrderByAttendanceDateDesc(3L, TODAY))
                .thenReturn(Optional.empty());
        when(attendances.saveAndFlush(any())).thenAnswer(invocation -> invocation.getArgument(0));

        assertFalse(service.checkIn(3L).isAlreadyCheckedIn());
        verify(users).resolveForUpdate(3L);
    }

    @Test
    void returnsRequestedMonthAndCurrentStreak() {
        User user = mock(User.class);
        UserAttendance today = new UserAttendance(user, TODAY, 4);
        when(attendances.findByUser_UserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
                4L, LocalDate.of(2026, 1, 1), LocalDate.of(2026, 1, 31))).thenReturn(List.of(today));
        when(attendances.findTopByUser_UserIdAndAttendanceDateBeforeOrderByAttendanceDateDesc(4L, TODAY.plusDays(1)))
                .thenReturn(Optional.of(today));

        var response = service.getMyAttendance(4L, YearMonth.of(2026, 1));

        assertEquals(YearMonth.of(2026, 1), response.getMonth());
        assertTrue(response.isCheckedInToday());
        assertEquals(4, response.getCurrentStreakCount());
        assertEquals(1, response.getDays().size());
    }
}
