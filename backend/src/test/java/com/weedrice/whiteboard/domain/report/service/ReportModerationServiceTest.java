package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.report.dto.MyReportResponse;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
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
import static org.mockito.Mockito.mockStatic;
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
    private ModerationActorResolver moderationActorResolver;
    @Mock
    private ReportReadAssembler reportReadAssembler;

    @InjectMocks
    private ReportModerationService reportModerationService;

    private User reporter;
    private User adminUser;
    private Admin admin;
    private Report report;
    private MockedStatic<SecurityUtils> mockedSecurityUtils;

    @BeforeEach
    void setUp() {
        reporter = User.builder().displayName("Reporter").build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);

        adminUser = User.builder().displayName("Admin").build();
        ReflectionTestUtils.setField(adminUser, "userId", 2L);
        adminUser.grantSuperAdminRole();

        admin = Admin.builder().user(adminUser).build();
        ReflectionTestUtils.setField(admin, "adminId", 5L);

        report = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(10L)
                .reasonType("SPAM")
                .build();
        ReflectionTestUtils.setField(report, "reportId", 7L);

        mockedSecurityUtils = mockStatic(SecurityUtils.class);
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).thenAnswer(invocation -> null);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
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
        mockedSecurityUtils.verify(SecurityUtils::validateSuperAdminPermission);
        verify(reportReadAssembler).toAdminResponsePage(reportPage);
    }

    @Test
    @DisplayName("getReports rejects when super admin guard fails")
    void getReports_guardFailure_throwsForbidden() {
        PageRequest pageable = PageRequest.of(0, 20);
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission)
                .thenThrow(new BusinessException(ErrorCode.FORBIDDEN));

        assertThatThrownBy(() -> reportModerationService.getReports(null, null, pageable))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.FORBIDDEN);

        mockedSecurityUtils.verify(SecurityUtils::validateSuperAdminPermission);
        verifyNoInteractions(reportRepository, reportReadAssembler);
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
    @DisplayName("processReport succeeds")
    void processReport_success() {
        ReportResponse response = ReportResponse.builder()
                .reportId(7L)
                .status(Report.STATUS_RESOLVED)
                .adminId(5L)
                .processorUserId(2L)
                .processedRemark("done")
                .build();

        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
        when(reportReadAssembler.toAdminResponse(report)).thenReturn(response);

        ReportResponse result = reportModerationService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done");

        assertThat(result.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
        assertThat(report.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
        assertThat(report.getAdmin()).isEqualTo(admin);
        assertThat(report.getProcessorUserId()).isEqualTo(2L);
        assertThat(report.getProcessedRemark()).isEqualTo("done");
        verify(reportRepository).findByIdForUpdate(7L);
    }

    @Test
    @DisplayName("processReport normalizes lowercase terminal statuses")
    void processReport_normalizesLowercaseStatus() {
        ReportResponse response = ReportResponse.builder()
                .reportId(7L)
                .status(Report.STATUS_RESOLVED)
                .build();

        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
        when(reportReadAssembler.toAdminResponse(report)).thenReturn(response);

        ReportResponse result = reportModerationService.processReport(2L, 7L, "resolved", "done");

        assertThat(result.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
        assertThat(report.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
    }

    @Test
    @DisplayName("processReport trims remark before save")
    void processReport_trimsRemarkBeforeSave() {
        ReportResponse response = ReportResponse.builder()
                .reportId(7L)
                .status(Report.STATUS_RESOLVED)
                .build();

        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));
        when(reportReadAssembler.toAdminResponse(report)).thenReturn(response);

        reportModerationService.processReport(2L, 7L, Report.STATUS_RESOLVED, "  done  ");

        assertThat(report.getProcessedRemark()).isEqualTo("done");
    }

    @Test
    @DisplayName("processReport rejects overlong remark")
    void processReport_overlongRemark_throwsValidationError() {
        assertThatThrownBy(() -> reportModerationService.processReport(
                2L, 7L, Report.STATUS_RESOLVED, "a".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("processReport records processorUserId without active admin")
    void processReport_withoutAdmin_recordsProcessorUserId() {
        ReportResponse response = ReportResponse.builder()
                .reportId(7L)
                .status(Report.STATUS_RESOLVED)
                .processorUserId(2L)
                .build();

        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, null));
        when(reportReadAssembler.toAdminResponse(report)).thenReturn(response);

        ReportResponse result = reportModerationService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done");

        assertThat(result.getProcessorUserId()).isEqualTo(2L);
        assertThat(report.getAdmin()).isNull();
        assertThat(report.getProcessorUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("processReport rejects already processed report as conflict")
    void processReport_alreadyProcessed_throwsConflict() {
        ReflectionTestUtils.setField(report, "status", Report.STATUS_RESOLVED);
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));

        assertThatThrownBy(() -> reportModerationService.processReport(2L, 7L, Report.STATUS_REJECTED, "retry"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("processReport accepts only terminal statuses")
    void processReport_invalidTerminalStatus_throwsValidationError() {
        assertThatThrownBy(() -> reportModerationService.processReport(2L, 7L, "APPROVED", "invalid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }
}
