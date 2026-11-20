package com.weedrice.whiteboard.domain.attendance.repository;

import com.weedrice.whiteboard.domain.attendance.entity.UserAttendance;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.config.QuerydslConfig;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.time.LocalDate;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class UserAttendanceRepositoryTest {
    @Autowired private UserAttendanceRepository attendances;
    @Autowired private EntityManager entityManager;

    @Test
    void selectsOnlyTheRequestedMonthAndCurrentStreakDatesForTheUser() {
        User user = user("calendar");
        User other = user("other");
        LocalDate today = LocalDate.of(2026, 3, 1);
        LocalDate january = LocalDate.of(2026, 1, 5);
        entityManager.persist(new UserAttendance(user, today, 2));
        entityManager.persist(new UserAttendance(user, today.minusDays(1), 1));
        entityManager.persist(new UserAttendance(user, today.minusDays(7), 1));
        entityManager.persist(new UserAttendance(user, january, 1));
        entityManager.persist(new UserAttendance(other, today, 5));
        entityManager.persist(new UserAttendance(other, january, 1));
        entityManager.flush();

        assertThat(attendances.findMonthAndCurrentStreakDays(user.getUserId(), january.withDayOfMonth(1),
                january.withDayOfMonth(31), today.minusDays(1), today)).extracting(UserAttendance::getAttendanceDate)
                .containsExactly(january, today.minusDays(1), today);
        assertThat(attendances.findMonthAndCurrentStreakDays(user.getUserId(), today,
                today.withDayOfMonth(31), today.minusDays(1), today)).extracting(UserAttendance::getAttendanceDate)
                .containsExactly(today.minusDays(1), today);
    }

    private User user(String loginId) {
        User user = User.builder().loginId(loginId).displayName(loginId)
                .email(loginId + "@example.org").password("test-password").build();
        entityManager.persist(user);
        return user;
    }
}
