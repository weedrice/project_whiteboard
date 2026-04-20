package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Map;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final ModerationActorResolver moderationActorResolver;
    private final SanctionService sanctionService;

    public ReportService(ReportRepository reportRepository,
                         UserRepository userRepository,
                         PostRepository postRepository,
                         CommentRepository commentRepository,
                         ModerationActorResolver moderationActorResolver,
                         SanctionService sanctionService) {
        this.reportRepository = reportRepository;
        this.userRepository = userRepository;
        this.postRepository = postRepository;
        this.commentRepository = commentRepository;
        this.sanctionService = sanctionService;
        this.moderationActorResolver = moderationActorResolver;
    }

    @Transactional
    public Long createReport(Long reporterId, String targetType, Long targetId, String reasonType, String remark,
            String contents) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(reporter);

        reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, targetType, targetId)
                .ifPresent(report -> {
                    throw new BusinessException(ErrorCode.ALREADY_REPORTED);
                });

        validateTarget(targetType, targetId);

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reasonType(reasonType)
                .remark(remark)
                .contents(contents)
                .build();
        try {
            return reportRepository.saveAndFlush(report).getReportId();
        } catch (DataIntegrityViolationException ex) {
            if (reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, targetType, targetId).isPresent()) {
                throw new BusinessException(ErrorCode.ALREADY_REPORTED);
            }
            throw ex;
        }
    }

    public Page<ReportResponse> getReports(String status, String targetType, Pageable pageable) {
        return toAdminResponsePage(reportRepository.findAdminReports(status, targetType, pageable));
    }

    public Page<MyReportResponse> getMyReports(Long userId, Pageable pageable) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return toMyReportResponsePage(reportRepository.findByReporterOrderByCreatedAtDesc(reporter, pageable));
    }

    private Page<ReportResponse> toAdminResponsePage(Page<Report> reports) {
        ReportTargetMetadata targetMetadata = loadTargetMetadata(reports.getContent());
        return reports.map(report -> toAdminResponse(report, targetMetadata));
    }

    private Page<MyReportResponse> toMyReportResponsePage(Page<Report> reports) {
        ReportTargetMetadata targetMetadata = loadTargetMetadata(reports.getContent());
        return reports.map(report -> toMyReportResponse(report, targetMetadata));
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

    @Transactional
    public ReportResponse processReport(Long adminUserId, Long reportId, String status, String remark) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Admin admin = moderationActorResolver.findActiveAdmin(adminUser).orElse(null);

        report.processReport(admin, adminUserId, status, remark);
        reportRepository.save(report);
        return toAdminResponse(report, loadTargetMetadata(List.of(report)));
    }

    private void validateTarget(String targetType, Long targetId) {
        switch (targetType.toUpperCase()) {
            case "POST":
                postRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.POST_NOT_FOUND));
                break;
            case "COMMENT":
                commentRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.COMMENT_NOT_FOUND));
                break;
            case "USER":
                userRepository.findById(targetId)
                        .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
                break;
            default:
                throw new BusinessException(ErrorCode.INVALID_TARGET,
                        "Invalid target type: " + targetType + ". Must be POST, COMMENT, or USER.");
        }
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
