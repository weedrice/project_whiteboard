package com.weedrice.whiteboard.domain.user.repository;

import com.weedrice.whiteboard.domain.auth.entity.LoginHistory;
import com.weedrice.whiteboard.domain.auth.repository.LoginHistoryRepository;
import com.weedrice.whiteboard.domain.board.repository.BoardSubscriptionRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.entity.Sanction;
import com.weedrice.whiteboard.domain.sanction.repository.SanctionRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class AdminUserDetailQueryRepository {

    private static final String REPORT_TARGET_TYPE_USER = "USER";
    private static final String REPORT_STATUS_PENDING = "PENDING";

    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final BoardSubscriptionRepository boardSubscriptionRepository;
    private final LoginHistoryRepository loginHistoryRepository;
    private final SanctionRepository sanctionRepository;
    private final ReportRepository reportRepository;

    public AdminUserDetailStats findStats(User user) {
        return new AdminUserDetailStats(
                postRepository.countByUserAndIsDeleted(user, false),
                commentRepository.countByUserAndIsDeleted(user, false),
                boardSubscriptionRepository.countByUser(user),
                loginHistoryRepository.findTopByUserAndIsSuccessTrueOrderByCreatedAtDesc(user).orElse(null),
                sanctionRepository.countByTargetUser(user),
                sanctionRepository.findTopByTargetUserOrderByCreatedAtDesc(user).orElse(null),
                reportRepository.countByTargetTypeAndTargetId(REPORT_TARGET_TYPE_USER, user.getUserId()),
                reportRepository.countByTargetTypeAndTargetIdAndStatus(
                        REPORT_TARGET_TYPE_USER,
                        user.getUserId(),
                        REPORT_STATUS_PENDING));
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
