package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class AdminUserDetailStatsReaderTest {

    @InjectMocks
    private AdminUserDetailStatsReader reader;

    @Mock private UserRepository userRepository;
    @Mock private LoginHistoryRepository loginHistoryRepository;
    @Mock private SanctionRepository sanctionRepository;

    @Test
    @DisplayName("read returns delegated count and recent values")
    void read_returnsDelegatedValues() {
        User user = User.builder()
                .loginId("target")
                .email("target@test.com")
                .password("pw")
                .displayName("target")
                .build();
        ReflectionTestUtils.setField(user, "userId", 1L);
        LoginHistory recentLogin = LoginHistory.builder().user(user).ipAddress("127.0.0.1").userAgent("agent").build();
        Sanction recentSanction = Sanction.builder().targetUser(user).type("BAN").remark("reason").build();

        when(userRepository.countAdminUserDetailStats(
                user,
                ReportTargetType.USER.name(),
                Report.STATUS_PENDING)).thenReturn(adminUserStats(1L, 2L, 3L, 4L, 5L, 6L));
        when(loginHistoryRepository.findTopByUserAndIsSuccessTrueOrderByCreatedAtDescHistoryIdDesc(user))
                .thenReturn(Optional.of(recentLogin));
        when(sanctionRepository.findTopByTargetUserOrderByCreatedAtDesc(user))
                .thenReturn(Optional.of(recentSanction));

        AdminUserDetailStatsReader.AdminUserDetailStats stats = reader.read(user);

        assertThat(stats.postCount()).isEqualTo(1L);
        assertThat(stats.commentCount()).isEqualTo(2L);
        assertThat(stats.subscriptionCount()).isEqualTo(3L);
        assertThat(stats.recentLogin()).isSameAs(recentLogin);
        assertThat(stats.sanctionCount()).isEqualTo(4L);
        assertThat(stats.recentSanction()).isSameAs(recentSanction);
        assertThat(stats.reportTotalCount()).isEqualTo(5L);
        assertThat(stats.reportPendingCount()).isEqualTo(6L);
    }

    private UserRepository.AdminUserDetailStatsProjection adminUserStats(
            long postCount,
            long commentCount,
            long subscriptionCount,
            long sanctionCount,
            long reportTotalCount,
            long reportPendingCount) {
        return new UserRepository.AdminUserDetailStatsProjection() {
            @Override
            public Long getPostCount() {
                return postCount;
            }

            @Override
            public Long getCommentCount() {
                return commentCount;
            }

            @Override
            public Long getSubscriptionCount() {
                return subscriptionCount;
            }

            @Override
            public Long getSanctionCount() {
                return sanctionCount;
            }

            @Override
            public Long getReportTotalCount() {
                return reportTotalCount;
            }

            @Override
            public Long getReportPendingCount() {
                return reportPendingCount;
            }
        };
    }
}
