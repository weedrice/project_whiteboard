package com.weedrice.whiteboard.domain.attendance.repository;

import com.weedrice.whiteboard.domain.attendance.entity.UserAttendance;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {

    boolean existsByUser_UserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    Optional<UserAttendance> findByUser_UserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    Optional<UserAttendance> findTopByUser_UserIdAndAttendanceDateBeforeOrderByAttendanceDateDesc(
            Long userId,
            LocalDate attendanceDate);

    List<UserAttendance> findByUser_UserIdAndAttendanceDateBetweenOrderByAttendanceDateAsc(
            Long userId,
            LocalDate startDate,
            LocalDate endDate);
}
