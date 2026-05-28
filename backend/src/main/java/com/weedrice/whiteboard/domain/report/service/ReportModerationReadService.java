package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportModerationReadService {

    private final ReportRepository reportRepository;
    private final ReportReadAssembler reportReadAssembler;

    public ReportResponse getAdminReportResponse(Long reportId) {
        Report report = reportRepository.findByReportId(reportId)
                .orElseThrow(() -> new BusinessException(ErrorCode.NOT_FOUND));
        return reportReadAssembler.toAdminResponse(report);
    }
}
