package com.weedrice.whiteboard.domain.user.service;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class AdminUserDetailStatsReader {

    private static final String REPORT_TARGET_TYPE_USER = ReportTargetType.USER.name();
    private static final String REPORT_STATUS_PENDING = Report.STATUS_PENDING;

    private final UserRepository userRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final SanctionRepository sanctionRepository;

    public AdminUserDetailStats read(User user) {
        UserRepository.AdminUserDetailStatsProjection stats = userRepository.countAdminUserDetailStats(
                user,
                REPORT_TARGET_TYPE_USER,
                REPORT_STATUS_PENDING);

        return new AdminUserDetailStats(
                countOrZero(stats.getPostCount()),
                countOrZero(stats.getCommentCount()),
                countOrZero(stats.getSubscriptionCount()),
                loginHistoryRepository.findTopByUserAndIsSuccessTrueOrderByCreatedAtDesc(user).orElse(null),
                countOrZero(stats.getSanctionCount()),
                sanctionRepository.findTopByTargetUserOrderByCreatedAtDesc(user).orElse(null),
                countOrZero(stats.getReportTotalCount()),
                countOrZero(stats.getReportPendingCount()));
    }

    private long countOrZero(Long count) {
        return count == null ? 0L : count;
    }

    public record AdminUserDetailStats(
            long postCount,
            long commentCount,
            long subscriptionCount,
            LoginHistory recentLogin,
            long sanctionCount,
            Sanction recentSanction,
            long reportTotalCount,
            long reportPendingCount) {
    }
}
