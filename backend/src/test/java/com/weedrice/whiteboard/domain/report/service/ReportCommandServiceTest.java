package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.constant.ReportConstraints;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.exception.BusinessException;
import com.weedrice.whiteboard.global.exception.ErrorCode;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCommandServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private ReportTargetValidator reportTargetValidator;
    @Mock
    private SanctionService sanctionService;

    @InjectMocks
    private ReportCommandService reportCommandService;

    @Test
    @DisplayName("createReport succeeds")
    void createReport_success() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        Report report = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("SPAM")
                .build();
        ReflectionTestUtils.setField(report, "reportId", 10L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(report);

        Long reportId = reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, "contents");

        assertThat(reportId).isEqualTo(10L);
        verify(reportTargetValidator).validate("POST", 2L, reporter);
        verify(reportRepository).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport normalizes targetType before duplicate checks and save")
    void createReport_normalizesTargetType() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        Report savedReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("SPAM")
                .build();
        ReflectionTestUtils.setField(savedReport, "reportId", 11L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(savedReport);

        Long reportId = reportCommandService.createReport(1L, "post", 2L, "SPAM", null, null);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, "POST", 2L, Report.STATUS_PENDING);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(reportId).isEqualTo(11L);
        assertThat(captor.getValue().getTargetType()).isEqualTo("POST");
    }

    @Test
    @DisplayName("createReport rejects invalid targetType as invalid target")
    void createReport_invalidTargetType_throwsInvalidTarget() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "article", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TARGET);

        verify(reportRepository, never()).existsByReporterAndTargetTypeAndTargetIdAndStatus(
                any(User.class), any(), any(), any());
        verify(reportTargetValidator, never()).validate(any(), any(), any());
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport normalizes reasonType before save")
    void createReport_normalizesReasonType() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        Report savedReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("ABUSE")
                .build();
        ReflectionTestUtils.setField(savedReport, "reportId", 12L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(savedReport);

        Long reportId = reportCommandService.createReport(1L, "POST", 2L, "abuse", null, null);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(reportId).isEqualTo(12L);
        assertThat(captor.getValue().getReasonType()).isEqualTo("ABUSE");
    }

    @Test
    @DisplayName("createReport trims remark before save")
    void createReport_trimsRemarkBeforeSave() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        Report savedReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("SPAM")
                .remark("details")
                .build();
        ReflectionTestUtils.setField(savedReport, "reportId", 13L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(savedReport);

        reportCommandService.createReport(1L, "POST", 2L, "SPAM", "  details  ", null);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getRemark()).isEqualTo("details");
    }

    @Test
    @DisplayName("createReport trims contents before save")
    void createReport_trimsContentsBeforeSave() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        Report savedReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("SPAM")
                .contents("details")
                .build();
        ReflectionTestUtils.setField(savedReport, "reportId", 14L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(savedReport);

        reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, "  details  ");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContents()).isEqualTo("details");
    }

    @Test
    @DisplayName("createReport stores blank contents as null")
    void createReport_blankContents_storesNull() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        Report savedReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("SPAM")
                .build();
        ReflectionTestUtils.setField(savedReport, "reportId", 15L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(savedReport);

        reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, "   ");

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(captor.getValue().getContents()).isNull();
    }

    @Test
    @DisplayName("createReport rejects overlong remark")
    void createReport_overlongRemark_throwsValidationError() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportCommandService.createReport(
                1L, "POST", 2L, "SPAM", "a".repeat(256), null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport rejects overlong contents")
    void createReport_overlongContents_throwsValidationError() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportCommandService.createReport(
                1L, "POST", 2L, "SPAM", null, "a".repeat(ReportConstraints.MAX_CONTENTS_LENGTH + 1)))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport rejects contents whose raw length exceeds the controller limit")
    void createReport_overlongRawContents_throwsValidationError() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        String paddedContents = " " + "a".repeat(ReportConstraints.MAX_CONTENTS_LENGTH) + " ";
        assertThatThrownBy(() -> reportCommandService.createReport(
                1L, "POST", 2L, "SPAM", null, paddedContents))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport defaults blank reasonType to ETC")
    void createReport_blankReasonType_defaultsToEtc() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        Report savedReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("ETC")
                .build();
        ReflectionTestUtils.setField(savedReport, "reportId", 13L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(savedReport);

        Long reportId = reportCommandService.createReport(1L, "POST", 2L, " ", null, null);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(reportId).isEqualTo(13L);
        assertThat(captor.getValue().getReasonType()).isEqualTo("ETC");
    }

    @Test
    @DisplayName("createReport rejects invalid reasonType")
    void createReport_invalidReasonType_throwsValidationError() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "INVALID", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.VALIDATION_ERROR);

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport maps duplicate conflict to already reported")
    void createReport_duplicateConflict_throwsAlreadyReported() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("constraint [uq_reports_pending_user_target]"));

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);
        verify(reportRepository).existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, "POST", 2L, Report.STATUS_PENDING);
    }

    @Test
    @DisplayName("createReport maps legacy duplicate constraint to already reported")
    void createReport_legacyDuplicateConflict_throwsAlreadyReported() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("constraint [uk_reports_user_target]"));

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);
    }

    @Test
    @DisplayName("createReport does not map unrelated data integrity violations")
    void createReport_unrelatedDataIntegrityViolation_rethrowsOriginalException() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("constraint [ck_reports_reason_type]"));

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, null))
                .isInstanceOf(DataIntegrityViolationException.class);
    }

    @Test
    @DisplayName("createReport rejects banned reporter")
    void createReport_bannedReporter_throwsUserNotActive() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(reporter);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("createReport rejects deleted or unreadable post targets")
    void createReport_unreadablePost_throwsPostNotFound() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(reportTargetValidator).validate("POST", 2L, reporter);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport rejects POST self-report as invalid target")
    void createReport_postSelfReport_throwsInvalidTarget() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        doThrow(new BusinessException(ErrorCode.INVALID_TARGET))
                .when(reportTargetValidator).validate("POST", 10L, reporter);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 10L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TARGET);

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport rejects COMMENT self-report as invalid target")
    void createReport_commentSelfReport_throwsInvalidTarget() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        doThrow(new BusinessException(ErrorCode.INVALID_TARGET))
                .when(reportTargetValidator).validate("COMMENT", 10L, reporter);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "COMMENT", 10L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.INVALID_TARGET);

        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport keeps duplicate precedence before USER target policy validation")
    void createReport_duplicateUserSelfReport_throwsAlreadyReported() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, "USER", 1L, Report.STATUS_PENDING)).thenReturn(true);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "USER", 1L, "ETC", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);

        verify(reportTargetValidator, never()).validate("USER", 1L, reporter);
    }

    @Test
    @DisplayName("createReport keeps duplicate precedence before POST target policy validation")
    void createReport_duplicatePostReport_throwsAlreadyReported() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, "POST", 10L, Report.STATUS_PENDING)).thenReturn(true);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 10L, "ETC", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);

        verify(reportTargetValidator, never()).validate("POST", 10L, reporter);
    }

    @Test
    @DisplayName("createReport keeps duplicate precedence before COMMENT target policy validation")
    void createReport_duplicateCommentReport_throwsAlreadyReported() {
        User reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.existsByReporterAndTargetTypeAndTargetIdAndStatus(
                reporter, "COMMENT", 10L, Report.STATUS_PENDING)).thenReturn(true);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "COMMENT", 10L, "ETC", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);

        verify(reportTargetValidator, never()).validate("COMMENT", 10L, reporter);
    }

    @Test
    @DisplayName("createReport rejects deleted or unreadable comment targets")
    void createReport_unreadableComment_throwsCommentNotFound() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        doThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND))
                .when(reportTargetValidator).validate("COMMENT", 2L, reporter);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "COMMENT", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.COMMENT_NOT_FOUND);
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport propagates parent post visibility failures for comment targets")
    void createReport_commentOnUnreadablePost_throwsPostNotFound() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(reportTargetValidator).validate("COMMENT", 2L, reporter);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "COMMENT", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }
}
