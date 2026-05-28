package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.verifyNoInteractions;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportModerationServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportReadAssembler reportReadAssembler;
    @Mock
    private ReportModerationCommandService reportModerationCommandService;
    @Mock
    private ReportModerationReadService reportModerationReadService;

    @InjectMocks
    private ReportModerationService reportModerationService;

    private User reporter;
    private Report report;

    @BeforeEach
    void setUp() {
        reporter = User.builder().displayName("Reporter").build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);

        report = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(10L)
                .reasonType("SPAM")
                .build();
        ReflectionTestUtils.setField(report, "reportId", 7L);
    }

    @Test
    @DisplayName("getReports uses assembler for admin responses")
    void getReports_usesAssembler() {
        PageRequest pageable = PageRequest.of(0, 20);
        PageRequest safePageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("reportId")));
        Page<Report> reportPage = new PageImpl<>(List.of(report), safePageable, 1);
        Page<ReportResponse> responsePage = new PageImpl<>(
                List.of(ReportResponse.builder().reportId(7L).build()),
                safePageable,
                1);

        when(reportRepository.findAdminReports("PENDING", "POST", safePageable)).thenReturn(reportPage);
        when(reportReadAssembler.toAdminResponsePage(reportPage)).thenReturn(responsePage);

        Page<ReportResponse> result = reportModerationService.getReports("PENDING", "POST", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reportReadAssembler).toAdminResponsePage(reportPage);
    }

    @Test
    @DisplayName("getReports normalizes filter values before querying repository")
    void getReports_normalizesFilters() {
        PageRequest pageable = PageRequest.of(0, 20);
        PageRequest safePageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("reportId")));
        Page<Report> reportPage = new PageImpl<>(List.of(report), safePageable, 1);
        Page<ReportResponse> responsePage = new PageImpl<>(
                List.of(ReportResponse.builder().reportId(7L).build()),
                safePageable,
                1);

        when(reportRepository.findAdminReports("PENDING", "POST", safePageable)).thenReturn(reportPage);
        when(reportReadAssembler.toAdminResponsePage(reportPage)).thenReturn(responsePage);

        Page<ReportResponse> result = reportModerationService.getReports("pending", "post", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reportRepository).findAdminReports("PENDING", "POST", safePageable);
    }

    @Test
    @DisplayName("getReports rejects invalid targetType as invalid target")
    void getReports_invalidTargetType_throwsInvalidTarget() {
        PageRequest pageable = PageRequest.of(0, 20);

        assertThatThrownBy(() -> reportModerationService.getReports(null, "article", pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TARGET);

        verifyNoInteractions(reportRepository, reportReadAssembler);
    }

    @Test
    @DisplayName("getReports limits page size and sort fields")
    void getReports_normalizesPageable() {
        PageRequest requested = PageRequest.of(2, 250, Sort.by(Sort.Order.asc("reporterId")));
        PageRequest safePageable = PageRequest.of(2, 100, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("reportId")));
        Page<Report> reportPage = Page.empty(safePageable);
        Page<ReportResponse> responsePage = Page.empty(safePageable);

        when(reportRepository.findAdminReports(null, null, safePageable)).thenReturn(reportPage);
        when(reportReadAssembler.toAdminResponsePage(reportPage)).thenReturn(responsePage);

        Page<ReportResponse> result = reportModerationService.getReports(null, null, requested);

        assertThat(result).isSameAs(responsePage);
        verify(reportRepository).findAdminReports(null, null, safePageable);
    }

    @Test
    @DisplayName("getMyReports loads reporter and assembles responses")
    void getMyReports_usesAssembler() {
        PageRequest pageable = PageRequest.of(0, 20);
        PageRequest safePageable = PageRequest.of(0, 20, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("reportId")));
        Page<Report> reportPage = new PageImpl<>(List.of(report), safePageable, 1);
        Page<MyReportResponse> responsePage = new PageImpl<>(
                List.of(MyReportResponse.builder().reportId(7L).targetType("POST").build()),
                safePageable,
                1);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterOrderByCreatedAtDescReportIdDesc(reporter, safePageable))
                .thenReturn(reportPage);
        when(reportReadAssembler.toMyReportResponsePage(reportPage)).thenReturn(responsePage);

        Page<MyReportResponse> result = reportModerationService.getMyReports(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReportId()).isEqualTo(7L);
        verify(reportReadAssembler).toMyReportResponsePage(reportPage);
    }

    @Test
    @DisplayName("getMyReports limits page size and uses report sort")
    void getMyReports_normalizesPageable() {
        PageRequest requested = PageRequest.of(2, 250, Sort.by(Sort.Order.asc("targetId")));
        PageRequest safePageable = PageRequest.of(2, 100, Sort.by(
                Sort.Order.desc("createdAt"),
                Sort.Order.desc("reportId")));
        Page<Report> reportPage = Page.empty(safePageable);
        Page<MyReportResponse> responsePage = Page.empty(safePageable);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterOrderByCreatedAtDescReportIdDesc(reporter, safePageable))
                .thenReturn(reportPage);
        when(reportReadAssembler.toMyReportResponsePage(reportPage)).thenReturn(responsePage);

        Page<MyReportResponse> result = reportModerationService.getMyReports(1L, requested);

        assertThat(result).isSameAs(responsePage);
        verify(reportRepository).findByReporterOrderByCreatedAtDescReportIdDesc(reporter, safePageable);
    }

    @Test
    @DisplayName("getMyReports rejects missing reporter")
    void getMyReports_missingReporter_throwsUserNotFound() {
        PageRequest pageable = PageRequest.of(0, 20);
        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> reportModerationService.getMyReports(1L, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_FOUND);
    }

    @Test
    @DisplayName("processReport separates command and read response assembly")
    void processReport_separatesCommandAndRead() {
        ReportResponse response = ReportResponse.builder()
                .reportId(7L)
                .status(Report.STATUS_RESOLVED)
                .build();

        when(reportModerationCommandService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done")).thenReturn(7L);
        when(reportModerationReadService.getAdminReportResponse(7L)).thenReturn(response);

        ReportResponse result = reportModerationService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done");

        assertThat(result.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
        verify(reportModerationCommandService).processReport(2L, 7L, Report.STATUS_RESOLVED, "done");
        verify(reportModerationReadService).getAdminReportResponse(7L);
    }
}
