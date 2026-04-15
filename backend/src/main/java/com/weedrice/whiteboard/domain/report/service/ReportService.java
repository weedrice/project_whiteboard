package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
public class ReportService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final AdminRepository adminRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;

    @Transactional
    public Long createReport(Long reporterId, String targetType, Long targetId, String reasonType, String remark,
            String contents) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

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
        return reportRepository.save(report).getReportId();
    }

    public Page<ReportResponse> getReports(String status, String targetType, Pageable pageable) {
        Page<Report> reports;
        if (status != null && !status.isEmpty() && targetType != null && !targetType.isEmpty()) {
            reports = reportRepository.findByTargetTypeAndStatusOrderByCreatedAtDesc(targetType, status, pageable);
        } else if (status != null && !status.isEmpty()) {
            reports = reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        } else {
            reports = reportRepository.findAll(pageable);
        }
        return reports.map(this::toResponse);
    }

    public Page<ReportResponse> getMyReports(Long userId, Pageable pageable) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return reportRepository.findByReporterOrderByCreatedAtDesc(reporter, pageable).map(this::toResponse);
    }

    private ReportResponse toResponse(Report report) {
        String targetDisplayName = null;
        String targetLoginId = null;
        if ("USER".equalsIgnoreCase(report.getTargetType())) {
            var targetUser = userRepository.findById(report.getTargetId());
            if (targetUser.isPresent()) {
                targetDisplayName = targetUser.get().getDisplayName();
                targetLoginId = targetUser.get().getLoginId();
            }
        }
        return ReportResponse.builder()
                .reportId(report.getReportId())
                .reporterId(report.getReporter().getUserId())
                .reporterDisplayName(report.getReporter().getDisplayName())
                .targetType(report.getTargetType())
                .targetId(report.getTargetId())
                .targetDisplayName(targetDisplayName)
                .targetLoginId(targetLoginId)
                .reasonType(report.getReasonType())
                .remark(report.getRemark())
                .processedRemark(report.getProcessedRemark())
                .status(report.getStatus())
                .contents(report.getContents())
                .createdAt(report.getCreatedAt())
                .updatedAt(report.getModifiedAt())
                .adminId(report.getAdmin() != null ? report.getAdmin().getAdminId() : null)
                .build();
    }

    @Transactional
    public ReportResponse processReport(Long adminUserId, Long reportId, String status, String remark) {
        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        Admin admin = adminRepository.findFirstByUserAndIsActiveOrderByAdminIdAsc(adminUser, true)
                .orElse(null);

        report.processReport(admin, status, remark);
        reportRepository.save(report);
        return toResponse(report);
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
}
