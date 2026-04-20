package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportCommandService reportCommandService;
    @Mock
    private ReportModerationService reportModerationService;

    @InjectMocks
    private ReportService reportService;

    @Test
    @DisplayName("ReportService delegates report creation to command service")
    void createReport_delegatesToCommandService() {
        when(reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, "contents")).thenReturn(10L);

        Long reportId = reportService.createReport(1L, "POST", 2L, "SPAM", null, "contents");

        assertThat(reportId).isEqualTo(10L);
        verify(reportCommandService).createReport(1L, "POST", 2L, "SPAM", null, "contents");
    }

    @Test
    @DisplayName("ReportService delegates admin report queries to moderation service")
    void getReports_delegatesToModerationService() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<ReportResponse> page = new PageImpl<>(List.of(ReportResponse.builder().reportId(1L).build()), pageable, 1);
        when(reportModerationService.getReports("PENDING", "POST", pageable)).thenReturn(page);

        Page<ReportResponse> result = reportService.getReports("PENDING", "POST", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reportModerationService).getReports("PENDING", "POST", pageable);
    }

    @Test
    @DisplayName("ReportService delegates my report queries to moderation service")
    void getMyReports_delegatesToModerationService() {
        PageRequest pageable = PageRequest.of(0, 20);
        Page<MyReportResponse> responsePage = new PageImpl<>(
                List.of(MyReportResponse.builder().reportId(3L).targetType("POST").build()),
                pageable,
                1);
        when(reportModerationService.getMyReports(1L, pageable)).thenReturn(responsePage);

        Page<MyReportResponse> result = reportService.getMyReports(1L, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getReportId()).isEqualTo(3L);
        verify(reportModerationService).getMyReports(1L, pageable);
    }

    @Test
    @DisplayName("ReportService delegates report processing to moderation service")
    void processReport_delegatesToModerationService() {
        ReportResponse response = ReportResponse.builder().reportId(7L).status(Report.STATUS_RESOLVED).build();
        when(reportModerationService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done"))
                .thenReturn(response);

        ReportResponse result = reportService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done");

        assertThat(result.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
        verify(reportModerationService).processReport(2L, 7L, Report.STATUS_RESOLVED, "done");
    }
}
