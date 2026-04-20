package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportCommandService {

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final PostRepository postRepository;
    private final CommentRepository commentRepository;
    private final SanctionService sanctionService;

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
