package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private AdminRepository adminRepository;

    @InjectMocks
    private ReportService reportService;

    private User reporter;
    private Admin admin;
    private Report report;

    @BeforeEach
    void setUp() {
        reporter = User.builder().build();
        admin = Admin.builder().user(User.builder().build()).build();
        report = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(1L)
                .reasonType("SPAM")
                .build();
    }

    @Test
    @DisplayName("신고 생성 성공")
    void createReport_success() {
        // given
        Long reporterId = 1L;
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(any(), any(), any())).thenReturn(Optional.empty());
        when(reportRepository.save(any(Report.class))).thenReturn(report);

        // when
        Report createdReport = reportService.createReport(reporterId, "POST", 1L, "SPAM", null, null);

        // then
        assertThat(createdReport.getTargetType()).isEqualTo("POST");
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    @DisplayName("신고 처리 성공")
    void processReport_success() {
        // given
        Long adminUserId = 1L;
        Long reportId = 1L;
        String status = "RESOLVED";
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(User.builder().build()));
        when(adminRepository.findByUserAndIsActive(any(), any())).thenReturn(Optional.of(admin));
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        // when
        Report processedReport = reportService.processReport(adminUserId, reportId, status, "Test Remark");

        // then
        assertThat(processedReport.getStatus()).isEqualTo(status);
        assertThat(processedReport.getAdmin()).isEqualTo(admin);
    }
}
