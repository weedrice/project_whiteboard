package com.weedrice.whiteboard.domain.attendance.repository;

import com.weedrice.whiteboard.domain.attendance.entity.UserAttendance;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDate;
import java.util.List;
import java.util.Optional;

public interface UserAttendanceRepository extends JpaRepository<UserAttendance, Long> {

    boolean existsByUser_UserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    Optional<UserAttendance> findByUser_UserIdAndAttendanceDate(Long userId, LocalDate attendanceDate);

    Optional<UserAttendance> findTopByUser_UserIdAndAttendanceDateBeforeOrderByAttendanceDateDesc(
            Long userId,
            LocalDate attendanceDate);

    @Query("""
            SELECT a FROM UserAttendance a
            WHERE a.user.userId = :userId
              AND (a.attendanceDate BETWEEN :monthStart AND :monthEnd
                   OR a.attendanceDate BETWEEN :yesterday AND :today)
            ORDER BY a.attendanceDate
            """)
    List<UserAttendance> findMonthAndCurrentStreakDays(
            @Param("userId") Long userId, @Param("monthStart") LocalDate monthStart,
            @Param("monthEnd") LocalDate monthEnd, @Param("yesterday") LocalDate yesterday,
            @Param("today") LocalDate today);
}
