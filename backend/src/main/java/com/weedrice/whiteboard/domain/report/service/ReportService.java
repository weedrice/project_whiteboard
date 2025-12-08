package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
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

    @Transactional
    public Report createReport(Long reporterId, String targetType, Long targetId, String reasonType, String remark,
            String contents) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // 중복 신고 방지
        reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, targetType, targetId)
                .ifPresent(report -> {
                    throw new BusinessException(ErrorCode.ALREADY_REPORTED);
                });

        // TODO: targetId와 targetType의 유효성 검사 (실제로 존재하는 게시글/댓글/사용자인지)

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(targetType)
                .targetId(targetId)
                .reasonType(reasonType)
                .remark(remark)
                .contents(contents)
                .build();
        return reportRepository.save(report);
    }

    public Page<Report> getReports(String status, String targetType, Pageable pageable) {
        SecurityUtils.validateSuperAdminPermission();

        if (status != null && !status.isEmpty() && targetType != null && !targetType.isEmpty()) {
            return reportRepository.findByTargetTypeAndStatusOrderByCreatedAtDesc(targetType, status, pageable);
        } else if (status != null && !status.isEmpty()) {
            return reportRepository.findByStatusOrderByCreatedAtDesc(status, pageable);
        }
        return reportRepository.findAll(pageable); // 모든 신고 조회
    }

    public Page<Report> getMyReports(Long userId, Pageable pageable) {
        User reporter = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        return reportRepository.findByReporterOrderByCreatedAtDesc(reporter, pageable);
    }

    @Transactional
    public Report processReport(Long adminUserId, Long reportId, String status, String remark) {
        SecurityUtils.validateSuperAdminPermission();

        User adminUser = userRepository.findById(adminUserId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));

        // Admin 테이블에 존재하면 가져오고, 없으면 null (슈퍼 관리자는 Admin 테이블에 없을 수 있음)
        Admin admin = adminRepository.findByUserAndIsActive(adminUser, true).orElse(null);

        Report report = reportRepository.findById(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));

        report.processReport(admin, status, remark);
        return report;
    }
}
