package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.service.CommentService;
import com.weedrice.whiteboard.domain.post.service.PostService;
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
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCommandServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostService postService;
    @Mock
    private CommentService commentService;
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
        com.weedrice.whiteboard.domain.post.entity.Post targetPost = mock(com.weedrice.whiteboard.domain.post.entity.Post.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "POST", 2L))
                .thenReturn(Optional.empty());
        when(postService.getPostById(2L, 1L, false)).thenReturn(targetPost);
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(report);

        Long reportId = reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, "contents");

        assertThat(reportId).isEqualTo(10L);
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
        com.weedrice.whiteboard.domain.post.entity.Post targetPost = mock(com.weedrice.whiteboard.domain.post.entity.Post.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "POST", 2L))
                .thenReturn(Optional.empty());
        when(postService.getPostById(2L, 1L, false)).thenReturn(targetPost);
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(savedReport);

        Long reportId = reportCommandService.createReport(1L, "post", 2L, "SPAM", null, null);

        ArgumentCaptor<Report> captor = ArgumentCaptor.forClass(Report.class);
        verify(reportRepository, times(1)).findByReporterAndTargetTypeAndTargetId(reporter, "POST", 2L);
        verify(reportRepository).saveAndFlush(captor.capture());
        assertThat(reportId).isEqualTo(11L);
        assertThat(captor.getValue().getTargetType()).isEqualTo("POST");
    }

    @Test
    @DisplayName("createReport maps duplicate conflict to already reported")
    void createReport_duplicateConflict_throwsAlreadyReported() {
        User reporter = User.builder().build();
        Report existingReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(2L)
                .reasonType("SPAM")
                .build();
        com.weedrice.whiteboard.domain.post.entity.Post targetPost = mock(com.weedrice.whiteboard.domain.post.entity.Post.class);

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "POST", 2L))
                .thenReturn(Optional.empty(), Optional.of(existingReport));
        when(postService.getPostById(2L, 1L, false)).thenReturn(targetPost);
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);
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
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "POST", 2L))
                .thenReturn(Optional.empty());
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(postService).getPostById(2L, 1L, false);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("createReport rejects deleted or unreadable comment targets")
    void createReport_unreadableComment_throwsCommentNotFound() {
        User reporter = User.builder().build();
        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "COMMENT", 2L))
                .thenReturn(Optional.empty());
        doThrow(new BusinessException(ErrorCode.COMMENT_NOT_FOUND))
                .when(commentService).getComment(2L, 1L);

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
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "COMMENT", 2L))
                .thenReturn(Optional.empty());
        doThrow(new BusinessException(ErrorCode.POST_NOT_FOUND))
                .when(commentService).getComment(2L, 1L);

        assertThatThrownBy(() -> reportCommandService.createReport(1L, "COMMENT", 2L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.POST_NOT_FOUND);
        verify(reportRepository, never()).saveAndFlush(any(Report.class));
    }
}
