package com.weedrice.whiteboard.domain.attendance.dto;

import com.weedrice.whiteboard.domain.attendance.entity.UserAttendance;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;
import java.time.YearMonth;
import java.util.List;

@Getter
@Builder
public class AttendanceMonthResponse {
    private YearMonth month;
    private LocalDate today;
    private boolean checkedInToday;
    private int currentStreakCount;
    private List<AttendanceDay> days;

    @Getter
    @Builder
    public static class AttendanceDay {
        private LocalDate attendanceDate;
        private int streakCount;

        public static AttendanceDay from(UserAttendance attendance) {
            return AttendanceDay.builder()
                    .attendanceDate(attendance.getAttendanceDate())
                    .streakCount(attendance.getStreakCount())
                    .build();
        }
    }
}
