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
        long totalUsers = userRepository.count();
        long totalPosts = postRepository.count();
        long pendingReports = reportRepository.countByStatus("PENDING");
        long activeUsers = userRepository.countByLastLoginAtAfter(LocalDateTime.now().minusDays(1));

        return DashboardStatsDto.builder()
                .totalUsers(totalUsers)
                .totalPosts(totalPosts)
                .pendingReports(pendingReports)
                .activeUsers(activeUsers)
                .build();
    }
}
