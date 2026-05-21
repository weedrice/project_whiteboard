package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.admin.dto.DashboardStatsDto;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.Role;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminDashboardService {

    private final PostRepository postRepository;
    private final ReportRepository reportRepository;
    private final UserRepository userRepository;

    @PreAuthorize("hasRole('" + Role.SUPER_ADMIN + "')")
    public DashboardStatsDto getDashboardStats() {
        UserRepository.AdminDashboardUserStatsProjection userStats =
                userRepository.countAdminDashboardUserStats(LocalDateTime.now().minusDays(1));
        long totalPosts = postRepository.countVisiblePostsForAdminDashboard();
        long pendingReports = reportRepository.countByStatus("PENDING");

        return DashboardStatsDto.builder()
                .totalUsers(userStats.getTotalUsers())
                .totalPosts(totalPosts)
                .pendingReports(pendingReports)
                .activeUsers(userStats.getActiveUsers())
                .build();
    }
}
