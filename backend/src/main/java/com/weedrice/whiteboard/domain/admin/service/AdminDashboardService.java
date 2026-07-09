package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.DashboardStatsDto;
import com.weedrice.whiteboard.domain.admin.dto.DeepDashboardStatsDto;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import jakarta.persistence.Query;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final EntityManager entityManager;
    private final Clock clock;

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public DashboardStatsDto getDashboardStats() {
        UserRepository.AdminDashboardUserStatsProjection userStats =
                userRepository.countAdminDashboardUserStats(LocalDateTime.now(clock).minusDays(1));
        long totalPosts = postRepository.countVisiblePostsForAdminDashboard();
        long pendingReports = reportRepository.countByStatus("PENDING");

        return DashboardStatsDto.builder()
                .totalUsers(userStats.getTotalUsers())
                .totalPosts(totalPosts)
                .pendingReports(pendingReports)
                .activeUsers(userStats.getActiveUsers())
                .build();
    }

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public DeepDashboardStatsDto getDeepDashboardStats(int requestedDays) {
        int days = requestedDays == 90 ? 90 : 30;
        LocalDate today = LocalDate.now(clock);
        LocalDate startDate = today.minusDays(days - 1L);
        LocalDateTime startDateTime = startDate.atStartOfDay();

        Map<LocalDate, DailyAccumulator> daily = new LinkedHashMap<>();
        for (int i = 0; i < days; i++) {
            daily.put(startDate.plusDays(i), new DailyAccumulator());
        }

        applyDailyCounts(daily, "users", "created_at", "status = 'ACTIVE' AND deleted_at IS NULL", startDateTime,
                DailyMetric.SIGNUPS);
        applyDailyCounts(daily, "posts", "created_at", "is_deleted = 'N'", startDateTime, DailyMetric.POSTS);
        applyDailyCounts(daily, "comments", "created_at", "is_deleted = 'N'", startDateTime, DailyMetric.COMMENTS);
        applyDailyCounts(daily, "reports", "created_at", "1 = 1", startDateTime, DailyMetric.REPORTS);

        return new DeepDashboardStatsDto(
                days,
                daily.entrySet().stream()
                        .map(entry -> entry.getValue().toDailyStats(entry.getKey()))
                        .toList(),
                loadTopBoards(startDateTime),
                loadModerationStats(startDateTime));
    }

    private void applyDailyCounts(
            Map<LocalDate, DailyAccumulator> daily,
            String tableName,
            String dateColumn,
            String whereClause,
            LocalDateTime startDateTime,
            DailyMetric metric) {
        Query query = entityManager.createNativeQuery("""
                SELECT CAST(%s AS DATE) AS stat_date, COUNT(*) AS total_count
                FROM %s
                WHERE %s >= :startDateTime
                  AND %s
                GROUP BY CAST(%s AS DATE)
                ORDER BY stat_date
                """.formatted(dateColumn, tableName, dateColumn, whereClause, dateColumn));
        query.setParameter("startDateTime", startDateTime);

        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            DailyAccumulator accumulator = daily.get(toLocalDate(columns[0]));
            if (accumulator != null) {
                accumulator.set(metric, toLong(columns[1]));
            }
        }
    }

    private List<DeepDashboardStatsDto.TopBoardStats> loadTopBoards(LocalDateTime startDateTime) {
        Query query = entityManager.createNativeQuery("""
                SELECT b.board_id,
                       b.board_name,
                       b.board_url,
                       COALESCE(p.post_count, 0) + COALESCE(c.comment_count, 0) AS activity_count
                FROM boards b
                LEFT JOIN (
                    SELECT board_id, COUNT(*) AS post_count
                    FROM posts
                    WHERE created_at >= :startDateTime
                      AND is_deleted = 'N'
                    GROUP BY board_id
                ) p ON p.board_id = b.board_id
                LEFT JOIN (
                    SELECT p2.board_id, COUNT(*) AS comment_count
                    FROM comments c2
                    JOIN posts p2 ON p2.post_id = c2.post_id
                    WHERE c2.created_at >= :startDateTime
                      AND c2.is_deleted = 'N'
                      AND p2.is_deleted = 'N'
                    GROUP BY p2.board_id
                ) c ON c.board_id = b.board_id
                WHERE b.is_active = 'Y'
                ORDER BY activity_count DESC, b.sort_order ASC, b.board_id ASC
                LIMIT 10
                """);
        query.setParameter("startDateTime", startDateTime);

        List<DeepDashboardStatsDto.TopBoardStats> result = new ArrayList<>();
        for (Object row : query.getResultList()) {
            Object[] columns = (Object[]) row;
            long activityCount = toLong(columns[3]);
            if (activityCount == 0) {
                continue;
            }
            result.add(new DeepDashboardStatsDto.TopBoardStats(
                    toLong(columns[0]),
                    (String) columns[1],
                    (String) columns[2],
                    activityCount));
        }
        return result;
    }

    private DeepDashboardStatsDto.ModerationStats loadModerationStats(LocalDateTime startDateTime) {
        Object[] reportCounts = (Object[]) entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(CASE WHEN status = 'PENDING' THEN 1 ELSE 0 END), 0) AS pending_reports,
                       COALESCE(SUM(CASE WHEN status = 'RESOLVED' THEN 1 ELSE 0 END), 0) AS resolved_reports,
                       COALESCE(SUM(CASE WHEN status = 'REJECTED' THEN 1 ELSE 0 END), 0) AS rejected_reports
                FROM reports
                WHERE created_at >= :startDateTime
                """)
                .setParameter("startDateTime", startDateTime)
                .getSingleResult();

        Object[] auditCounts = (Object[]) entityManager.createNativeQuery("""
                SELECT COALESCE(SUM(CASE WHEN action IN ('POST_AUTO_BLIND', 'COMMENT_AUTO_BLIND') THEN 1 ELSE 0 END), 0) AS auto_blinds,
                       COALESCE(SUM(CASE WHEN action = 'POST_BLIND' THEN 1 ELSE 0 END), 0) AS manager_blinds
                FROM moderation_audit_logs
                WHERE created_at >= :startDateTime
                """)
                .setParameter("startDateTime", startDateTime)
                .getSingleResult();

        return new DeepDashboardStatsDto.ModerationStats(
                toLong(reportCounts[0]),
                toLong(reportCounts[1]),
                toLong(reportCounts[2]),
                toLong(auditCounts[0]),
                toLong(auditCounts[1]));
    }

    private LocalDate toLocalDate(Object value) {
        if (value instanceof LocalDate localDate) {
            return localDate;
        }
        if (value instanceof java.sql.Date sqlDate) {
            return sqlDate.toLocalDate();
        }
        return LocalDate.parse(value.toString());
    }

    private long toLong(Object value) {
        return value == null ? 0L : ((Number) value).longValue();
    }

    private enum DailyMetric {
        SIGNUPS,
        POSTS,
        COMMENTS,
        REPORTS
    }

    private static class DailyAccumulator {
        private long signups;
        private long posts;
        private long comments;
        private long reports;

        void set(DailyMetric metric, long count) {
            switch (metric) {
                case SIGNUPS -> signups = count;
                case POSTS -> posts = count;
                case COMMENTS -> comments = count;
                case REPORTS -> reports = count;
            }
        }

        DeepDashboardStatsDto.DailyStats toDailyStats(LocalDate date) {
            return new DeepDashboardStatsDto.DailyStats(date, signups, posts, comments, reports);
        }
    }
}
