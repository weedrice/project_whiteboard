package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.common.service.CommonCodeReader;
import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.entity.ReportReasonType;
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

    private static final String REPORT_REASON_COMMON_CODE_TYPE = "REPORT_REASON";

    private final ReportRepository reportRepository;
    private final UserRepository userRepository;
    private final ReportTargetValidator reportTargetValidator;
    private final SanctionService sanctionService;
    private final ReportAutoBlindService reportAutoBlindService;
    private final CommonCodeReader commonCodeReader;

    @Transactional
    public Long createReport(Long reporterId, String targetType, Long targetId, String reasonType, String remark,
            String contents) {
        User reporter = userRepository.findById(reporterId)
                .orElseThrow(() -> new BusinessException(ErrorCode.USER_NOT_FOUND));
        sanctionService.validateNotBanned(reporter);
        String normalizedTargetType = ReportTargetTypeNormalizer.normalizeRequired(targetType);
        String normalizedReasonType = normalizeReasonType(reasonType);
        String normalizedRemark = ReportRemarkNormalizer.normalize(remark);
        String normalizedContents = normalizeContents(contents);

        reportAutoBlindService.lockTarget(normalizedTargetType, targetId);
        reportTargetValidator.validate(normalizedTargetType, targetId, reporter);

        ReportDuplicatePolicy.validateNoPendingDuplicate(reportRepository, reporter, normalizedTargetType, targetId);

        Report report = Report.builder()
                .reporter(reporter)
                .targetType(normalizedTargetType)
                .targetId(targetId)
                .reasonType(normalizedReasonType)
                .remark(normalizedRemark)
                .contents(normalizedContents)
                .build();
        try {
            Long reportId = reportRepository.saveAndFlush(report).getReportId();
            reportAutoBlindService.applyIfThresholdReached(normalizedTargetType, targetId);
            return reportId;
        } catch (DataIntegrityViolationException ex) {
            if (ReportDuplicatePolicy.isDuplicateConflict(ex)) {
                throw ReportDuplicatePolicy.alreadyReported();
            }
            throw ex;
        }
    }

    private String normalizeReasonType(String reasonType) {
        String normalizedReasonType;
        if (reasonType == null || reasonType.isBlank()) {
            normalizedReasonType = ReportReasonType.ETC.name();
        } else {
            try {
                normalizedReasonType = ReportReasonType.from(reasonType).name();
            } catch (IllegalArgumentException ex) {
                throw new BusinessException(ErrorCode.VALIDATION_ERROR);
            }
        }

        if (!commonCodeReader.isActiveDetail(REPORT_REASON_COMMON_CODE_TYPE, normalizedReasonType)) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        return normalizedReasonType;
    }

    private String normalizeContents(String contents) {
        if (contents == null) {
            return null;
        }
        if (contents.length() > ReportConstraints.MAX_CONTENTS_LENGTH) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        String normalizedContents = contents.strip();
        if (normalizedContents.isBlank()) {
            return null;
        }
        return normalizedContents;
    }

}
