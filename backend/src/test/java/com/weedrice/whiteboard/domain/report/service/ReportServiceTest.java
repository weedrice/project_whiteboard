package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.domain.user.repository.UserRepository;
import com.weedrice.whiteboard.global.common.util.SecurityUtils;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyBoolean;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.mockStatic;
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
    @Mock
    private PostRepository postRepository;
    @Mock
    private CommentRepository commentRepository;

    @InjectMocks
    private ReportService reportService;

    private User reporter;
    private User adminUser;
    private Admin admin;
    private Report report;
    private MockedStatic<SecurityUtils> mockedSecurityUtils;

    @BeforeEach
    void setUp() {
        reporter = User.builder().build();
        ReflectionTestUtils.setField(reporter, "userId", 1L);

        adminUser = User.builder().build();
        ReflectionTestUtils.setField(adminUser, "userId", 2L);

        admin = Admin.builder().user(adminUser).build();
        ReflectionTestUtils.setField(admin, "adminId", 1L);
        report = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(1L)
                .reasonType("SPAM")
                .build();

        mockedSecurityUtils = mockStatic(SecurityUtils.class);
    }

    @AfterEach
    void tearDown() {
        mockedSecurityUtils.close();
    }

    @Test
    @DisplayName("신고 생성 성공")
    void createReport_success() {
        // given
        Long reporterId = 1L;
        Long targetId = 1L;
        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(any(User.class), anyString(), anyLong())).thenReturn(Optional.empty());
        when(postRepository.findById(targetId)).thenReturn(Optional.of(com.weedrice.whiteboard.domain.post.entity.Post.builder()
                .board(com.weedrice.whiteboard.domain.board.entity.Board.builder().build())
                .user(reporter)
                .title("Test")
                .contents("Test")
                .build()));
        ReflectionTestUtils.setField(report, "reportId", 1L);
        when(reportRepository.save(any(Report.class))).thenReturn(report);

        // when
        Long createdReportId = reportService.createReport(reporterId, "POST", targetId, "SPAM", null, null);

        // then
        assertThat(createdReportId).isNotNull();
        verify(reportRepository).save(any(Report.class));
    }

    @Test
    @DisplayName("신고 처리 성공")
    void processReport_success() {
        // given
        Long adminUserId = 2L;
        Long reportId = 1L;
        String status = "RESOLVED";
        mockedSecurityUtils.when(SecurityUtils::validateSuperAdminPermission).then(invocation -> null);
        when(userRepository.findById(adminUserId)).thenReturn(Optional.of(adminUser));
        when(adminRepository.findByUserAndIsActive(any(User.class), anyBoolean())).thenReturn(Optional.of(admin));
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));

        // when
        ReportResponse processedReport = reportService.processReport(adminUserId, reportId, status, "Test Remark");

        // then
        assertThat(processedReport.getStatus()).isEqualTo(status);
        assertThat(processedReport.getAdminId()).isNotNull();
    }

    @Test
    @DisplayName("내 신고 목록 조회 성공")
    void getMyReports_success() {
        // given
        Long userId = 1L;
        org.springframework.data.domain.Pageable pageable = org.springframework.data.domain.PageRequest.of(0, 10);
        org.springframework.data.domain.Page<Report> reportPage = new org.springframework.data.domain.PageImpl<>(
                java.util.Collections.singletonList(report), pageable, 1);
        when(userRepository.findById(userId)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterOrderByCreatedAtDesc(reporter, pageable)).thenReturn(reportPage);

        // when
        org.springframework.data.domain.Page<ReportResponse> result = reportService.getMyReports(userId, pageable);

        // then
        assertThat(result.getContent()).isNotEmpty();
        verify(userRepository).findById(userId);
    }
}
