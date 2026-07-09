package com.weedrice.whiteboard.domain.attendance.controller;

import com.weedrice.whiteboard.domain.attendance.dto.AttendanceCheckInResponse;
import com.weedrice.whiteboard.domain.attendance.dto.AttendanceMonthResponse;
import com.weedrice.whiteboard.domain.attendance.service.AttendanceService;
import com.weedrice.whiteboard.global.common.ApiResponse;
import com.weedrice.whiteboard.global.security.CurrentUserId;
import lombok.RequiredArgsConstructor;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.YearMonth;

@RestController
@RequestMapping("/api/v1/attendance")
@RequiredArgsConstructor
public class AttendanceController {

    private final AttendanceService attendanceService;

    @PostMapping("/check-in")
    public ApiResponse<AttendanceCheckInResponse> checkIn(@CurrentUserId Long userId) {
        return ApiResponse.success(attendanceService.checkIn(userId));
    }

    @GetMapping("/me")
    public ApiResponse<AttendanceMonthResponse> getMyAttendance(
            @RequestParam(required = false)
            @DateTimeFormat(pattern = "yyyy-MM") YearMonth month,
            @CurrentUserId Long userId) {
        return ApiResponse.success(attendanceService.getMyAttendance(userId, month));
    }
}
