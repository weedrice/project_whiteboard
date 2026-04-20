package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
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
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ReportCommandServiceTest {

    @Mock
    private ReportRepository reportRepository;
    @Mock
    private UserRepository userRepository;
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;
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
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "POST", 2L))
                .thenReturn(Optional.empty());
        when(postRepository.findById(2L)).thenReturn(Optional.of(Post.builder()
                .board(com.weedrice.whiteboard.domain.board.entity.Board.builder().creator(reporter).build())
                .user(reporter)
                .title("title")
                .contents("contents")
                .build()));
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(report);

        Long reportId = reportCommandService.createReport(1L, "POST", 2L, "SPAM", null, "contents");

        assertThat(reportId).isEqualTo(10L);
        verify(reportRepository).saveAndFlush(any(Report.class));
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

        when(userRepository.findById(1L)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "POST", 2L))
                .thenReturn(Optional.empty(), Optional.of(existingReport));
        when(postRepository.findById(2L)).thenReturn(Optional.of(Post.builder()
                .board(com.weedrice.whiteboard.domain.board.entity.Board.builder().creator(reporter).build())
                .user(reporter)
                .title("title")
                .contents("contents")
                .build()));
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
}
