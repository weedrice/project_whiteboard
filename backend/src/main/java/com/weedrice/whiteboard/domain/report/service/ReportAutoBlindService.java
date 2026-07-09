package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class ReportAutoBlindService {

    private static final String AUTO_BLIND_THRESHOLD_CONFIG = "REPORT_AUTO_BLIND_THRESHOLD";
    private static final int DEFAULT_AUTO_BLIND_THRESHOLD = 5;
    private static final String AUTO_REPORT_REASON = "AUTO_REPORT";

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationAuditLogService moderationAuditLogService;
    private final GlobalConfigService globalConfigService;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    void applyIfThresholdReached(String targetType, Long targetId) {
        ReportTargetType normalizedTargetType = ReportTargetType.from(targetType);
        if (normalizedTargetType == ReportTargetType.USER) {
            return;
        }
        long pendingReportCount = reportRepository.countByTargetTypeAndTargetIdAndStatus(
                normalizedTargetType.name(),
                targetId,
                Report.STATUS_PENDING);
        if (pendingReportCount < resolveThreshold()) {
            return;
        }
        if (normalizedTargetType == ReportTargetType.POST) {
            blindPost(targetId);
            return;
        }
        blindComment(targetId);
    }

    private int resolveThreshold() {
        return GlobalConfigService.parseIntConfigOrDefault(
                globalConfigService.getConfig(AUTO_BLIND_THRESHOLD_CONFIG),
                DEFAULT_AUTO_BLIND_THRESHOLD,
                1);
    }

    private void blindPost(Long postId) {
        Post post = postRepository.findByIdWithRelationsForBlindUpdate(postId).orElse(null);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted()) || Boolean.TRUE.equals(post.getIsBlinded())) {
            return;
        }
        post.blind(AUTO_REPORT_REASON, LocalDateTime.now(clock));
        moderationAuditLogService.recordSystemAction(
                ModerationAuditLogService.ACTION_POST_AUTO_BLIND,
                ModerationAuditLogService.TARGET_TYPE_POST,
                post.getPostId(),
                post.getBoard(),
                AUTO_REPORT_REASON);
    }

    private void blindComment(Long commentId) {
        Comment comment = commentRepository.findByIdWithRelationsForBlindUpdate(commentId).orElse(null);
        if (comment == null || Boolean.TRUE.equals(comment.getIsDeleted())
                || Boolean.TRUE.equals(comment.getIsBlinded())) {
            return;
        }
        comment.blind(AUTO_REPORT_REASON, LocalDateTime.now(clock));
        moderationAuditLogService.recordSystemAction(
                ModerationAuditLogService.ACTION_COMMENT_AUTO_BLIND,
                ModerationAuditLogService.TARGET_TYPE_COMMENT,
                comment.getCommentId(),
                comment.getPost().getBoard(),
                AUTO_REPORT_REASON);
    }
}
