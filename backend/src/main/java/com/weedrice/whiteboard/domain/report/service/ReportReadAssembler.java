package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Component
@RequiredArgsConstructor
class ReportReadAssembler {

    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    public Page<ReportResponse> toAdminResponsePage(Page<Report> reports) {
        ReportTargetMetadata targetMetadata = loadTargetMetadata(reports.getContent(), true);
        return reports.map(report -> toAdminResponse(report, targetMetadata));
    }

    public Page<MyReportResponse> toMyReportResponsePage(Page<Report> reports) {
        ReportTargetMetadata targetMetadata = loadTargetMetadata(reports.getContent(), true);
        return reports.map(report -> toMyReportResponse(report, targetMetadata));
    }

    public ReportResponse toAdminResponse(Report report) {
        return toAdminResponse(report, loadTargetMetadata(List.of(report), true));
    }

    private ReportResponse toAdminResponse(Report report, ReportTargetMetadata targetMetadata) {
        ReportTargetUserMetadata targetUser = resolveTargetUser(report, targetMetadata);
        Long targetUserId = targetUser != null ? targetUser.targetUserId() : null;

        return ReportResponse.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporter().getUserId())
                .reporterDisplayName(report.getReporter().getDisplayName())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetUserId(targetUserId)
                .targetDisplayName(targetUser != null ? targetUser.targetDisplayName() : null)
                .targetLoginId(targetUser != null ? targetUser.targetLoginId() : null)
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
        return MyReportResponse.builder()
                .reportId(report.getReportId())
                .targetType(report.getTargetType())
                .targetDisplayName(resolveTargetDisplayName(report, targetMetadata))
                .reasonType(report.getReasonType())
                .status(report.getStatus())
                .contents(report.getContents())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .build();
    }

    private ReportTargetMetadata loadTargetMetadata(List<Report> reports, boolean includeContentAuthorUsers) {
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

        Map<Long, ReportTargetUserMetadata> userTargets = userTargetIds.isEmpty()
                ? Map.of()
                : userRepository.findReportTargetMetadataByUserIds(userTargetIds).stream()
                        .map(this::toUserMetadata)
                        .collect(Collectors.toMap(ReportTargetUserMetadata::targetId, metadata -> metadata));
        Map<Long, ReportTargetUserMetadata> postTargets = includeContentAuthorUsers && !postTargetIds.isEmpty()
                ? postRepository.findReportTargetMetadataByPostIds(postTargetIds).stream()
                        .map(this::toUserMetadata)
                        .collect(Collectors.toMap(ReportTargetUserMetadata::targetId, metadata -> metadata))
                : Map.of();
        Map<Long, ReportTargetUserMetadata> commentTargets = includeContentAuthorUsers && !commentTargetIds.isEmpty()
                ? commentRepository.findReportTargetMetadataByCommentIds(commentTargetIds).stream()
                        .map(this::toUserMetadata)
                        .collect(Collectors.toMap(ReportTargetUserMetadata::targetId, metadata -> metadata))
                : Map.of();

        return new ReportTargetMetadata(userTargets, postTargets, commentTargets);
    }

    private Long resolveTargetUserId(Report report, ReportTargetMetadata targetMetadata) {
        ReportTargetUserMetadata targetUser = resolveTargetUser(report, targetMetadata);
        return targetUser != null ? targetUser.targetUserId() : null;
    }

    private ReportTargetUserMetadata resolveTargetUser(Report report, ReportTargetMetadata targetMetadata) {
        if (isUserTarget(report)) {
            return targetMetadata.userTargets()
                    .getOrDefault(report.getTargetId(), ReportTargetUserMetadata.missingUser(report.getTargetId()));
        }
        if (isPostTarget(report)) {
            return targetMetadata.postTargets().get(report.getTargetId());
        }
        if (isCommentTarget(report)) {
            return targetMetadata.commentTargets().get(report.getTargetId());
        }
        return null;
    }

    private String resolveTargetDisplayName(Report report, ReportTargetMetadata targetMetadata) {
        Long targetUserId = resolveTargetUserId(report, targetMetadata);
        if (targetUserId == null) {
            return null;
        }
        ReportTargetUserMetadata targetUser = resolveTargetUser(report, targetMetadata);
        return targetUser != null ? targetUser.targetDisplayName() : null;
    }

    private ReportTargetUserMetadata toUserMetadata(UserRepository.ReportTargetMetadataProjection projection) {
        return new ReportTargetUserMetadata(
                projection.getTargetId(),
                projection.getTargetUserId(),
                projection.getTargetDisplayName(),
                projection.getTargetLoginId());
    }

    private ReportTargetUserMetadata toUserMetadata(PostRepository.ReportTargetMetadataProjection projection) {
        return new ReportTargetUserMetadata(
                projection.getTargetId(),
                projection.getTargetUserId(),
                projection.getTargetDisplayName(),
                projection.getTargetLoginId());
    }

    private ReportTargetUserMetadata toUserMetadata(CommentRepository.ReportTargetMetadataProjection projection) {
        return new ReportTargetUserMetadata(
                projection.getTargetId(),
                projection.getTargetUserId(),
                projection.getTargetDisplayName(),
                projection.getTargetLoginId());
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
            Map<Long, ReportTargetUserMetadata> userTargets,
            Map<Long, ReportTargetUserMetadata> postTargets,
            Map<Long, ReportTargetUserMetadata> commentTargets) {
    }

    private record ReportTargetUserMetadata(
            Long targetId,
            Long targetUserId,
            String targetDisplayName,
            String targetLoginId) {
        private static ReportTargetUserMetadata missingUser(Long targetId) {
            return new ReportTargetUserMetadata(targetId, targetId, null, null);
        }
    }
}
