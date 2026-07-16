package com.weedrice.whiteboard.domain.attendance.service;

import com.weedrice.whiteboard.domain.attendance.dto.AttendanceCheckInResponse;
import com.weedrice.whiteboard.domain.attendance.dto.AttendanceMonthResponse;
import com.weedrice.whiteboard.domain.attendance.entity.UserAttendance;
import com.weedrice.whiteboard.domain.attendance.repository.UserAttendanceRepository;
import com.weedrice.whiteboard.domain.badge.service.BadgeEvaluationService;
import com.weedrice.whiteboard.domain.point.service.ContentRewardPolicy;
import com.weedrice.whiteboard.domain.point.service.ContentRewardService;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserWritableResolver;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AttendanceService {

    private final UserAttendanceRepository attendanceRepository;
    private final UserWritableResolver userWritableResolver;
    private final SanctionService sanctionService;
    private final ContentRewardService contentRewardService;
    private final Clock clock;
    private final BadgeEvaluationService badgeEvaluationService;

    @Transactional
    public AttendanceCheckInResponse checkIn(Long userId) {
        User user = userWritableResolver.resolveForUpdate(userId);
        sanctionService.validateNotBanned(user);
        LocalDate today = LocalDate.now(clock);

        UserAttendance existingAttendance = attendanceRepository
                .findByUser_UserIdAndAttendanceDate(userId, today)
                .orElse(null);
        if (existingAttendance != null) {
            return response(existingAttendance, true, 0);
        }

        int streakCount = calculateStreakCount(userId, today);
        UserAttendance attendance = attendanceRepository.saveAndFlush(
                new UserAttendance(user, today, streakCount));

        int earnedPoints = rewardAttendance(userId, attendance.getAttendanceId(), streakCount);
        badgeEvaluationService.evaluateAttendanceStreakBadges(userId, streakCount);
        return response(attendance, false, earnedPoints);
    }

    public AttendanceMonthResponse getMyAttendance(Long userId, YearMonth month) {
        userWritableResolver.resolve(userId);
        LocalDate today = LocalDate.now(clock);
        YearMonth targetMonth = month != null ? month : YearMonth.from(today);
        LocalDate startDate = targetMonth.atDay(1);
        LocalDate endDate = targetMonth.atEndOfMonth();
        List<UserAttendance> attendances = attendanceRepository
                .findByUser_UserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(userId, startDate, endDate);

        boolean checkedInToday = attendances.stream()
                .anyMatch(attendance -> attendance.getAttendanceDate().equals(today));
        int currentStreakCount = attendanceRepository.findTopByUser_UserIdAndAttendanceDateBeforeOrderByAttendanceDateDesc(
                        userId,
                        today.plusDays(1))
                .map(UserAttendance::getStreakCount)
                .orElse(0);

        return AttendanceMonthResponse.builder()
                .month(targetMonth)
                .today(today)
                .checkedInToday(checkedInToday)
                .currentStreakCount(currentStreakCount)
                .days(attendances.stream()
                        .map(AttendanceMonthResponse.AttendanceDay::from)
                        .toList())
                .build();
    }

    private int calculateStreakCount(Long userId, LocalDate today) {
        return attendanceRepository
                .findTopByUser_UserIdAndAttendanceDateBeforeOrderByAttendanceDateDesc(userId, today)
                .filter(previous -> previous.getAttendanceDate().equals(today.minusDays(1)))
                .map(previous -> previous.getStreakCount() + 1)
                .orElse(1);
    }

    private int rewardAttendance(Long userId, Long attendanceId, int streakCount) {
        int earnedPoints = contentRewardService.rewardCreate(userId, attendanceId, ContentRewardPolicy.ATTENDANCE);
        if (streakCount == 7) {
            earnedPoints += contentRewardService.rewardCreate(userId, attendanceId,
                    ContentRewardPolicy.ATTENDANCE_STREAK_7);
        }
        if (streakCount == 30) {
            earnedPoints += contentRewardService.rewardCreate(userId, attendanceId,
                    ContentRewardPolicy.ATTENDANCE_STREAK_30);
        }
        return earnedPoints;
    }

    private AttendanceCheckInResponse response(UserAttendance attendance, boolean alreadyCheckedIn, int earnedPoints) {
        return AttendanceCheckInResponse.builder()
                .attendanceDate(attendance.getAttendanceDate())
                .streakCount(attendance.getStreakCount())
                .checkedIn(true)
                .alreadyCheckedIn(alreadyCheckedIn)
                .earnedPoints(earnedPoints)
                .build();
    }
}
