package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.admin.entity.Admin;
import com.weedrice.whiteboard.domain.admin.repository.AdminRepository;
import com.weedrice.whiteboard.domain.admin.service.ModerationActorResolver;
import com.weedrice.whiteboard.domain.comment.repository.CommentRepository;
import com.weedrice.whiteboard.domain.post.repository.PostRepository;
import com.weedrice.whiteboard.domain.report.dto.ReportResponse;
import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import com.weedrice.whiteboard.domain.sanction.service.SanctionService;
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
import org.springframework.dao.DataIntegrityViolationException;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import org.springframework.test.util.ReflectionTestUtils;

import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.lenient;
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
    @Mock
    private ModerationActorResolver moderationActorResolver;
    @Mock
    private SanctionService sanctionService;

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
        lenient().when(moderationActorResolver.findActiveAdmin(adminUser)).thenReturn(Optional.of(admin));
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
        when(reportRepository.saveAndFlush(any(Report.class))).thenReturn(report);

        // when
        Long createdReportId = reportService.createReport(reporterId, "POST", targetId, "SPAM", null, null);

        // then
        assertThat(createdReportId).isNotNull();
        verify(reportRepository).saveAndFlush(any(Report.class));
    }

    @Test
    @DisplayName("동시 신고 충돌은 이미 신고함 오류로 정규화한다")
    void createReport_duplicateConflict_throwsAlreadyReported() {
        Long reporterId = 1L;
        Long targetId = 1L;

        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        when(reportRepository.findByReporterAndTargetTypeAndTargetId(reporter, "POST", targetId))
                .thenReturn(Optional.empty(), Optional.of(report));
        when(postRepository.findById(targetId)).thenReturn(Optional.of(com.weedrice.whiteboard.domain.post.entity.Post.builder()
                .board(com.weedrice.whiteboard.domain.board.entity.Board.builder().build())
                .user(reporter)
                .title("Test")
                .contents("Test")
                .build()));
        when(reportRepository.saveAndFlush(any(Report.class)))
                .thenThrow(new DataIntegrityViolationException("duplicate"));

        assertThatThrownBy(() -> reportService.createReport(reporterId, "POST", targetId, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.ALREADY_REPORTED);
    }

    @Test
    @DisplayName("제재 중인 사용자는 신고를 생성할 수 없다")
    void createReport_bannedReporter_throwsUserNotActive() {
        Long reporterId = 1L;

        when(userRepository.findById(reporterId)).thenReturn(Optional.of(reporter));
        doThrow(new BusinessException(ErrorCode.USER_NOT_ACTIVE)).when(sanctionService).validateNotBanned(reporter);

        assertThatThrownBy(() -> reportService.createReport(reporterId, "POST", 1L, "SPAM", null, null))
                .isInstanceOf(BusinessException.class)
                .hasFieldOrPropertyWithValue("errorCode", ErrorCode.USER_NOT_ACTIVE);
    }

    @Test
    @DisplayName("신고 처리 성공")
    void processReport_success() {
        // given
        Long reportId = 1L;
        String status = "RESOLVED";
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));

        // when
        ReportResponse processedReport = reportService.processReport(2L, reportId, status, "Test Remark");

        // then
        assertThat(processedReport.getStatus()).isEqualTo(status);
        assertThat(processedReport.getAdminId()).isEqualTo(1L);
        assertThat(processedReport.getProcessorUserId()).isEqualTo(2L);
        assertThat(processedReport.getProcessedRemark()).isEqualTo("Test Remark");
    }

    @Test
    @DisplayName("targetType 단독 필터로 관리자 신고 목록을 조회한다")
    void getReports_filtersByTargetTypeOnly() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Report> reportPage = new PageImpl<>(List.of(report), pageable, 1);
        when(reportRepository.findAdminReports(null, "POST", pageable)).thenReturn(reportPage);

        Page<ReportResponse> result = reportService.getReports(null, "POST", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reportRepository).findAdminReports(null, "POST", pageable);
    }

    @Test
    @DisplayName("status 단독 필터로 관리자 신고 목록을 조회한다")
    void getReports_filtersByStatusOnly() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Report> reportPage = new PageImpl<>(List.of(report), pageable, 1);
        when(reportRepository.findAdminReports("PENDING", null, pageable)).thenReturn(reportPage);

        Page<ReportResponse> result = reportService.getReports("PENDING", null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reportRepository).findAdminReports("PENDING", null, pageable);
    }

    @Test
    @DisplayName("status 와 targetType 복합 필터로 관리자 신고 목록을 조회한다")
    void getReports_filtersByStatusAndTargetType() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Report> reportPage = new PageImpl<>(List.of(report), pageable, 1);
        when(reportRepository.findAdminReports("PENDING", "POST", pageable)).thenReturn(reportPage);

        Page<ReportResponse> result = reportService.getReports("PENDING", "POST", pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reportRepository).findAdminReports("PENDING", "POST", pageable);
    }

    @Test
    @DisplayName("필터 없이 관리자 신고 목록을 조회한다")
    void getReports_withoutFilters() {
        PageRequest pageable = PageRequest.of(0, 10);
        Page<Report> reportPage = new PageImpl<>(List.of(report), pageable, 1);
        when(reportRepository.findAdminReports(null, null, pageable)).thenReturn(reportPage);

        Page<ReportResponse> result = reportService.getReports(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        verify(reportRepository).findAdminReports(null, null, pageable);
    }

    @Test
    @DisplayName("활성 Admin 이 없어도 processorUserId 로 감사 추적을 남긴다")
    void processReport_savesProcessorUserIdWithoutAdmin() {
        Long reportId = 1L;
        when(reportRepository.findById(reportId)).thenReturn(Optional.of(report));
        when(userRepository.findById(2L)).thenReturn(Optional.of(adminUser));
        when(moderationActorResolver.findActiveAdmin(adminUser)).thenReturn(Optional.empty());

        ReportResponse processedReport = reportService.processReport(2L, reportId, "RESOLVED", "Test Remark");

        assertThat(processedReport.getAdminId()).isNull();
        assertThat(processedReport.getProcessorUserId()).isEqualTo(2L);
    }

    @Test
    @DisplayName("USER 대상 신고에만 대상 사용자 표시 정보를 채운다")
    void getReports_populatesTargetUserMetadataOnlyForUserReports() {
        PageRequest pageable = PageRequest.of(0, 10);

        User targetUser = User.builder()
                .loginId("target-user")
                .displayName("Target User")
                .build();
        ReflectionTestUtils.setField(targetUser, "userId", 30L);

        Report userTargetReport = Report.builder()
                .reporter(reporter)
                .targetType("USER")
                .targetId(30L)
                .reasonType("ABUSE")
                .build();
        ReflectionTestUtils.setField(userTargetReport, "reportId", 2L);

        Report postTargetReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(30L)
                .reasonType("SPAM")
                .build();
        ReflectionTestUtils.setField(postTargetReport, "reportId", 3L);

        when(reportRepository.findAdminReports(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(userTargetReport, postTargetReport), pageable, 2));
        when(userRepository.findAllById(List.of(30L))).thenReturn(List.of(targetUser));

        Page<ReportResponse> result = reportService.getReports(null, null, pageable);

        assertThat(result.getContent()).hasSize(2);
        assertThat(result.getContent().get(0).getTargetDisplayName()).isEqualTo("Target User");
        assertThat(result.getContent().get(0).getTargetLoginId()).isEqualTo("target-user");
        assertThat(result.getContent().get(1).getTargetDisplayName()).isNull();
        assertThat(result.getContent().get(1).getTargetLoginId()).isNull();
    }

    @Test
    @DisplayName("POST 대상 신고는 대상 작성자 사용자 ID를 응답에 포함한다")
    void getReports_includesPostAuthorUserIdAsTargetUserId() {
        PageRequest pageable = PageRequest.of(0, 10);

        Report postTargetReport = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(100L)
                .reasonType("SPAM")
                .build();
        ReflectionTestUtils.setField(postTargetReport, "reportId", 4L);

        User postAuthor = User.builder()
                .loginId("post-author")
                .displayName("Post Author")
                .build();
        ReflectionTestUtils.setField(postAuthor, "userId", 44L);

        com.weedrice.whiteboard.domain.post.entity.Post post = com.weedrice.whiteboard.domain.post.entity.Post.builder()
                .board(com.weedrice.whiteboard.domain.board.entity.Board.builder().creator(reporter).build())
                .user(postAuthor)
                .title("Reported Post")
                .contents("content")
                .build();
        ReflectionTestUtils.setField(post, "postId", 100L);

        when(reportRepository.findAdminReports(null, null, pageable))
                .thenReturn(new PageImpl<>(List.of(postTargetReport), pageable, 1));
        when(postRepository.findByPostIdIn(List.of(100L))).thenReturn(List.of(post));

        Page<ReportResponse> result = reportService.getReports(null, null, pageable);

        assertThat(result.getContent()).hasSize(1);
        assertThat(result.getContent().get(0).getTargetUserId()).isEqualTo(44L);
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
