package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.moderation.service.ModerationAuditLogService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportModerationCommandServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private ModerationActorResolver moderationActorResolver;
    @Mock
    private ModerationAuditLogService moderationAuditLogService;
    @Mock
    private ReportAutoBlindService reportAutoBlindService;

    @InjectMocks
    private ReportModerationCommandService reportModerationCommandService;

    private User reporter;
    private User adminUser;
    private Admin admin;
    private Report report;

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
    }

    @Test
    @DisplayName("processReport updates report and returns reportId")
    void processReport_success() {
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));

        Long result = reportModerationCommandService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done");

        assertThat(result).isEqualTo(7L);
        assertThat(report.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
        assertThat(report.getAdmin()).isEqualTo(admin);
        assertThat(report.getProcessorUserId()).isEqualTo(2L);
        assertThat(report.getProcessedRemark()).isEqualTo("done");
        verify(reportRepository).findByIdForUpdate(7L);
        verify(reportRepository).save(report);
        verify(moderationAuditLogService).recordUserAction(
                adminUser,
                ModerationAuditLogService.ACTION_REPORT_RESOLVE,
                ModerationAuditLogService.TARGET_TYPE_REPORT,
                7L,
                null,
                "done");
    }

    @Test
    @DisplayName("processReport normalizes lowercase terminal statuses")
    void processReport_normalizesLowercaseStatus() {
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));

        reportModerationCommandService.processReport(2L, 7L, "resolved", "done");

        assertThat(report.getStatus()).isEqualTo(Report.STATUS_RESOLVED);
    }

    @Test
    @DisplayName("processReport records rejection reason in moderation audit log")
    void processReport_rejected_recordsAuditReason() {
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));

        reportModerationCommandService.processReport(2L, 7L, Report.STATUS_REJECTED, "  no evidence  ");

        verify(moderationAuditLogService).recordUserAction(
                adminUser,
                ModerationAuditLogService.ACTION_REPORT_REJECT,
                ModerationAuditLogService.TARGET_TYPE_REPORT,
                7L,
                null,
                "no evidence");
        verify(reportAutoBlindService).restoreAutoBlindedCommentIfEligible("POST", 10L);
    }

    @Test
    @DisplayName("processReport trims remark before save")
    void processReport_trimsRemarkBeforeSave() {
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, admin));

        reportModerationCommandService.processReport(2L, 7L, Report.STATUS_RESOLVED, "  done  ");

        assertThat(report.getProcessedRemark()).isEqualTo("done");
    }

    @Test
    @DisplayName("processReport rejects overlong remark")
    void processReport_overlongRemark_throwsValidationError() {
        assertThatThrownBy(() -> reportModerationCommandService.processReport(
                2L, 7L, Report.STATUS_RESOLVED, "a".repeat(256)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }

    @Test
    @DisplayName("processReport records processorUserId without active admin")
    void processReport_withoutAdmin_recordsProcessorUserId() {
        when(reportRepository.findByIdForUpdate(7L)).thenReturn(Optional.of(report));
        when(moderationActorResolver.resolveModerationActor(2L))
                .thenReturn(new ModerationActorResolver.ModerationActor(adminUser, null));

        Long result = reportModerationCommandService.processReport(2L, 7L, Report.STATUS_RESOLVED, "done");

        assertThat(result).isEqualTo(7L);
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

        assertThatThrownBy(() -> reportModerationCommandService.processReport(
                2L, 7L, Report.STATUS_REJECTED, "retry"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.REPORT_ALREADY_PROCESSED);
    }

    @Test
    @DisplayName("processReport accepts only terminal statuses")
    void processReport_invalidTerminalStatus_throwsValidationError() {
        assertThatThrownBy(() -> reportModerationCommandService.processReport(2L, 7L, "APPROVED", "invalid"))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);
    }
}
