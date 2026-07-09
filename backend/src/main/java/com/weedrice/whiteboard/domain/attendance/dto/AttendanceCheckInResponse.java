package com.weedrice.whiteboard.domain.attendance.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class AttendanceCheckInResponse {
    private LocalDate attendanceDate;
    private int streakCount;
    private boolean checkedIn;
    private boolean alreadyCheckedIn;
    private int earnedPoints;
}
