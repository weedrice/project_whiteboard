package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ReportReadAssembler {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public Page<ReportResponse> toAdminResponsePage(Page<Report> reports) {
        ReportTargetMetadata targetMetadata = loadTargetMetadata(reports.getContent());
        return reports.map(report -> toAdminResponse(report, targetMetadata));
    }

    public Page<MyReportResponse> toMyReportResponsePage(Page<Report> reports) {
        ReportTargetMetadata targetMetadata = loadTargetMetadata(reports.getContent());
        return reports.map(report -> toMyReportResponse(report, targetMetadata));
    }

    public ReportResponse toAdminResponse(Report report) {
        return toAdminResponse(report, loadTargetMetadata(List.of(report)));
    }

    private ReportResponse toAdminResponse(Report report, ReportTargetMetadata targetMetadata) {
        User targetUser = isUserTarget(report)
                ? targetMetadata.userTargets().get(report.getTargetId())
                : null;

        return ReportResponse.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporter().getUserId())
                .reporterDisplayName(report.getReporter().getDisplayName())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetUserId(resolveTargetUserId(report, targetMetadata))
                .targetDisplayName(targetUser != null ? targetUser.getDisplayName() : null)
                .targetLoginId(targetUser != null ? targetUser.getLoginId() : null)
                .reasonType(report.getReasonType())
                .remark(report.getRemark())
                .processedRemark(report.getProcessedRemark())
                .status(report.getStatus())
                .contents(report.getContents())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .adminId(report.getAdmin() != null ? report.getAdmin().getAdminId() : null)
                .processorUserId(report.getProcessorUserId())
                .build();
    }

    private MyReportResponse toMyReportResponse(Report report, ReportTargetMetadata targetMetadata) {
        User targetUser = isUserTarget(report)
                ? targetMetadata.userTargets().get(report.getTargetId())
                : null;

        return MyReportResponse.builder()
                .reportId(report.getReportId())
                .targetType(report.getTargetType())
                .targetDisplayName(targetUser != null ? targetUser.getDisplayName() : null)
                .reasonType(report.getReasonType())
                .status(report.getStatus())
                .contents(report.getContents())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .build();
    }

    private ReportTargetMetadata loadTargetMetadata(List<Report> reports) {
        List<Long> userTargetIds = reports.stream()
                .filter(this::isUserTarget)
                .map(Report::getTargetId)
                .distinct()
                .toList();
        List<Long> postTargetIds = reports.stream()
                .filter(this::isPostTarget)
                .map(Report::getTargetId)
                .distinct()
                .toList();
        List<Long> commentTargetIds = reports.stream()
                .filter(this::isCommentTarget)
                .map(Report::getTargetId)
                .distinct()
                .toList();

        Map<Long, User> userTargets = userTargetIds.isEmpty()
                ? Map.of()
                : userRepository.findAllById(userTargetIds).stream()
                        .collect(Collectors.toMap(User::getUserId, Function.identity()));

        Map<Long, Long> postTargetUserIds = postTargetIds.isEmpty()
                ? Map.of()
                : postRepository.findByPostIdIn(postTargetIds).stream()
                        .collect(Collectors.toMap(Post::getPostId, post -> post.getUser().getUserId()));

        Map<Long, Long> commentTargetUserIds = commentTargetIds.isEmpty()
                ? Map.of()
                : commentRepository.findByCommentIdIn(commentTargetIds).stream()
                        .collect(Collectors.toMap(Comment::getCommentId, comment -> comment.getUser().getUserId()));

        return new ReportTargetMetadata(userTargets, postTargetUserIds, commentTargetUserIds);
    }

    private Long resolveTargetUserId(Report report, ReportTargetMetadata targetMetadata) {
        if (isUserTarget(report)) {
            User targetUser = targetMetadata.userTargets().get(report.getTargetId());
            return targetUser != null ? targetUser.getUserId() : report.getTargetId();
        }
        if (isPostTarget(report)) {
            return targetMetadata.postTargetUserIds().get(report.getTargetId());
        }
        if (isCommentTarget(report)) {
            return targetMetadata.commentTargetUserIds().get(report.getTargetId());
        }
        return null;
    }

    private boolean isUserTarget(Report report) {
        return hasTargetType(report, "USER");
    }

    private boolean isPostTarget(Report report) {
        return hasTargetType(report, "POST");
    }

    private boolean isCommentTarget(Report report) {
        return hasTargetType(report, "COMMENT");
    }

    private boolean hasTargetType(Report report, String targetType) {
        return report != null
                && StringUtils.hasText(report.getTargetType())
                && targetType.equalsIgnoreCase(report.getTargetType());
    }

    private record ReportTargetMetadata(
            Map<Long, User> userTargets,
            Map<Long, Long> postTargetUserIds,
            Map<Long, Long> commentTargetUserIds) {
    }
}
