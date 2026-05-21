package com.weedrice.whiteboard.domain.admin.service;

import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminDashboardServiceTest {

    @Mock
    private PostRepository postRepository;

    @Mock
    private ReportRepository reportRepository;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private AdminDashboardService adminDashboardService;

    @Test
    @DisplayName("대시보드 통계를 조회한다")
    void getDashboardStats_success() {
        UserRepository.AdminDashboardUserStatsProjection userStats =
                new UserRepository.AdminDashboardUserStatsProjection() {
                    @Override
                    public Long getTotalUsers() {
                        return 50L;
                    }

                    @Override
                    public Long getActiveUsers() {
                        return 10L;
                    }
                };
        when(postRepository.countVisiblePostsForAdminDashboard()).thenReturn(100L);
        when(reportRepository.countByStatus("PENDING")).thenReturn(5L);
        when(userRepository.countAdminDashboardUserStats(any(LocalDateTime.class))).thenReturn(userStats);

        var stats = adminDashboardService.getDashboardStats();

        assertThat(stats).isNotNull();
        assertThat(stats.getTotalUsers()).isEqualTo(50L);
        assertThat(stats.getTotalPosts()).isEqualTo(100L);
        assertThat(stats.getPendingReports()).isEqualTo(5L);
        assertThat(stats.getActiveUsers()).isEqualTo(10L);
        verify(postRepository).countVisiblePostsForAdminDashboard();
        verify(reportRepository).countByStatus("PENDING");
        verify(userRepository).countAdminDashboardUserStats(any(LocalDateTime.class));
        verify(userRepository, never()).countActiveUsersForAdminDashboard();
        verify(userRepository, never()).countRecentlyLoggedInActiveUsersForAdminDashboard(any(LocalDateTime.class));
    }
}
