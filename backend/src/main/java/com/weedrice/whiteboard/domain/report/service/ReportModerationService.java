package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.board.entity.Board;
import com.weedrice.whiteboard.domain.board.repository.BoardRepository;
import com.weedrice.whiteboard.domain.board.service.BoardAccessPolicy;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.service.UserReadableResolver;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import com.weedrice.whiteboard.global.common.util.PageRequestUtils;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Set;

@Service
@RequiredArgsConstructor
@Transactional(readOnly = true)
class ReportModerationService {
    private static final int DEFAULT_REPORT_PAGE_SIZE = 20;
    private static final Sort DEFAULT_REPORT_SORT = Sort.by(
            Sort.Order.desc("createdAt"),
            Sort.Order.desc("reportId"));
    private static final Set<String> ALLOWED_REPORT_SORTS = Set.of(
            "createdAt",
            "reportId",
            "status",
            "targetType",
            "targetId",
            "reasonType");

    private final ReportRepository reportRepository;
    private final BoardRepository boardRepository;
    private final BoardAccessPolicy boardAccessPolicy;
    private final UserReadableResolver userReadableResolver;
    private final ReportReadAssembler reportReadAssembler;
    private final ReportModerationCommandService reportModerationCommandService;
    private final ReportModerationReadService reportModerationReadService;

    public Page<ReportResponse> getReports(String status, String targetType, Pageable pageable) {
        String normalizedStatus = ReportStatusNormalizer.normalizeNullable(status);
        String normalizedTargetType = ReportTargetTypeNormalizer.normalizeNullable(targetType);
        Pageable safePageable = normalizeReportPageable(pageable);
        return reportReadAssembler.toAdminResponsePage(
                reportRepository.findAdminReports(normalizedStatus, normalizedTargetType, safePageable));
    }

    public Page<ReportResponse> getBoardReports(Long managerUserId, String boardUrl, String status,
            String targetType, Pageable pageable) {
        User manager = userReadableResolver.resolve(managerUserId);
        Board board = boardRepository.findByBoardUrl(boardUrl)
                .orElseThrow(() -> new BusinessException(ErrorCode.BOARD_NOT_FOUND));
        boardAccessPolicy.validateBoardAdmin(board, manager);
        String normalizedStatus = ReportStatusNormalizer.normalizeNullable(status);
        String normalizedTargetType = ReportTargetTypeNormalizer.normalizeNullable(targetType);
        Pageable safePageable = normalizeReportPageable(pageable);
        return reportReadAssembler.toAdminResponsePage(
                reportRepository.findBoardReports(board.getBoardId(), normalizedStatus, normalizedTargetType,
                        safePageable));
    }

    private Pageable normalizeReportPageable(Pageable pageable) {
        return PageRequestUtils.of(pageable, DEFAULT_REPORT_PAGE_SIZE, DEFAULT_REPORT_SORT, ALLOWED_REPORT_SORTS);
    }

    private Pageable normalizeMyReportPageable(Pageable pageable) {
        return PageRequestUtils.of(pageable, DEFAULT_REPORT_PAGE_SIZE, DEFAULT_REPORT_SORT);
    }

    public Page<MyReportResponse> getMyReports(Long userId, Pageable pageable) {
        User reporter = userReadableResolver.resolve(userId);
        Pageable safePageable = normalizeMyReportPageable(pageable);
        return reportReadAssembler.toMyReportResponsePage(
                reportRepository.findByReporterOrderByCreatedAtDescReportIdDesc(reporter, safePageable));
    }

    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    public ReportResponse processReport(Long adminUserId, Long reportId, String status, String remark) {
        Long processedReportId = reportModerationCommandService.processReport(adminUserId, reportId, status, remark);
        return reportModerationReadService.getAdminReportResponse(processedReportId);
    }

}
