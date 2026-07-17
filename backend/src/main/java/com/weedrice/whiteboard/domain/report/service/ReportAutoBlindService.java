package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.notification.service.NotificationAccessInvalidationService;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportTargetType;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchEventPublisher;
import com.weedrice.whiteboard.domain.search.semantic.SemanticSearchIndexAction;
import com.weedrice.whiteboard.global.common.service.GlobalConfigService;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Clock;
import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
class ReportAutoBlindService {

    private static final String AUTO_BLIND_THRESHOLD_CONFIG =
            GlobalConfigService.REPORT_AUTO_BLIND_THRESHOLD_CONFIG_KEY;
    private static final int DEFAULT_AUTO_BLIND_THRESHOLD = 5;
    private static final String AUTO_REPORT_REASON = "AUTO_REPORT";

    private final ReportRepository reportRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final UserRepository userRepository;
    private final ModerationAuditLogService moderationAuditLogService;
    private final GlobalConfigService globalConfigService;
    private final SemanticSearchEventPublisher semanticSearchEventPublisher;
    private final NotificationAccessInvalidationService notificationAccessInvalidationService;
    private final Clock clock;

    @Transactional(propagation = Propagation.MANDATORY)
    void lockTarget(String targetType, Long targetId) {
        ReportTargetType normalizedTargetType = ReportTargetType.from(targetType);
        if (normalizedTargetType == ReportTargetType.POST) {
            Post post = postRepository.findByIdWithRelationsForBlindUpdate(targetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
            if (Boolean.TRUE.equals(post.getIsDeleted())) {
                throw new BusinessException(ErrorCode.POST_NOT_FOUND);
            }
        } else if (normalizedTargetType == ReportTargetType.COMMENT) {
            Comment comment = commentRepository.findByIdWithRelationsForBlindUpdate(targetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
            if (Boolean.TRUE.equals(comment.getIsDeleted())) {
                throw new BusinessException(ErrorCode.COMMENT_NOT_FOUND);
            }
        } else if (normalizedTargetType == ReportTargetType.USER) {
            User user = userRepository.findByIdForUpdate(targetId)
                    .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
            if (!User.STATUS_ACTIVE.equals(user.getStatus()) || user.getDeletedAt() != null) {
                throw new BusinessException(ErrorCode.USER_NOT_FOUND);
            }
        }
    }

    @Transactional(propagation = Propagation.MANDATORY)
    void applyIfThresholdReached(String targetType, Long targetId) {
        ReportTargetType normalizedTargetType = ReportTargetType.from(targetType);
        if (normalizedTargetType == ReportTargetType.USER) {
            return;
        }
        int threshold = resolveThreshold();
        if (normalizedTargetType == ReportTargetType.POST) {
            blindPost(targetId, threshold);
            return;
        }
        blindComment(targetId, threshold);
    }

    private int resolveThreshold() {
        return GlobalConfigService.parseIntConfigOrDefault(
                globalConfigService.getConfig(AUTO_BLIND_THRESHOLD_CONFIG),
                DEFAULT_AUTO_BLIND_THRESHOLD,
                1);
    }

    private void blindPost(Long postId, int threshold) {
        Post post = postRepository.findByIdWithRelationsForBlindUpdate(postId).orElse(null);
        if (post == null || Boolean.TRUE.equals(post.getIsDeleted()) || Boolean.TRUE.equals(post.getIsBlinded())) {
            return;
        }
        if (pendingReportCount(ReportTargetType.POST, postId) < threshold) {
            return;
        }
        post.blind(AUTO_REPORT_REASON, LocalDateTime.now(clock));
        notificationAccessInvalidationService.invalidateCommentTopicAfterCommit(post.getPostId());
        semanticSearchEventPublisher.publish("POST", post.getPostId(), SemanticSearchIndexAction.DELETE);
        moderationAuditLogService.recordSystemAction(
                ModerationAuditLogService.ACTION_POST_AUTO_BLIND,
                ModerationAuditLogService.TARGET_TYPE_POST,
                post.getPostId(),
                post.getBoard(),
                AUTO_REPORT_REASON);
    }

    private void blindComment(Long commentId, int threshold) {
        Comment comment = commentRepository.findByIdWithRelationsForBlindUpdate(commentId).orElse(null);
        if (comment == null || Boolean.TRUE.equals(comment.getIsDeleted())
                || Boolean.TRUE.equals(comment.getIsBlinded())) {
            return;
        }
        if (pendingReportCount(ReportTargetType.COMMENT, commentId) < threshold) {
            return;
        }
        comment.blind(AUTO_REPORT_REASON, LocalDateTime.now(clock));
        semanticSearchEventPublisher.publish("COMMENT", comment.getCommentId(), SemanticSearchIndexAction.DELETE);
        moderationAuditLogService.recordSystemAction(
                ModerationAuditLogService.ACTION_COMMENT_AUTO_BLIND,
                ModerationAuditLogService.TARGET_TYPE_COMMENT,
                comment.getCommentId(),
                comment.getPost().getBoard(),
                AUTO_REPORT_REASON);
    }

    private long pendingReportCount(ReportTargetType targetType, Long targetId) {
        return reportRepository.countByTargetTypeAndTargetIdAndStatus(
                targetType.name(), targetId, Report.STATUS_PENDING);
    }
}
