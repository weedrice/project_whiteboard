package com.weedrice.whiteboard.domain.report.repository;

import com.weedrice.whiteboard.domain.report.entity.Report;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

import java.util.Optional;

import com.weedrice.whiteboard.global.config.QuerydslConfig;
import org.springframework.context.annotation.Import;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class ReportRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ReportRepository reportRepository;

    private User reporter;
    private Report report;

    @BeforeEach
    void setUp() {
        reporter = User.builder()
                .loginId("reporter")
                .email("reporter@test.com")
                .password("password")
                .displayName("Reporter")
                .build();
        entityManager.persist(reporter);

        report = Report.builder()
                .reporter(reporter)
                .targetType("POST")
                .targetId(1L)
                .reasonType("SPAM")
                .remark("Test report")
                .build();
        entityManager.persist(report);
        entityManager.flush();
    }

    @Test
    @DisplayName("신고 조회 - reporter, targetType, targetId로 조회")
    void findByReporterAndTargetTypeAndTargetId_success() {
        // when
        Optional<Report> found = reportRepository.findByReporterAndTargetTypeAndTargetId(
                reporter, "POST", 1L);

        // then
        assertThat(found).isPresent();
        assertThat(found.get().getReporter()).isEqualTo(reporter);
        assertThat(found.get().getTargetType()).isEqualTo("POST");
        assertThat(found.get().getTargetId()).isEqualTo(1L);
    }

    @Test
    @DisplayName("상태별 신고 개수 조회")
    void countByStatus_success() {
        // when
        long count = reportRepository.countByStatus("PENDING");

        // then
        assertThat(count).isGreaterThanOrEqualTo(1);
    }

    @Test
    @DisplayName("신고 처리용 락 조회는 대상 신고를 반환한다")
    void findByIdForUpdate_success() {
        Optional<Report> found = reportRepository.findByIdForUpdate(report.getReportId());

        assertThat(found).isPresent();
        assertThat(found.get().getReportId()).isEqualTo(report.getReportId());
    }

    @Test
    @DisplayName("상태별 신고 목록 조회")
    void findByStatusOrderByCreatedAtDesc_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Report> reports = reportRepository.findByStatusOrderByCreatedAtDesc("PENDING", pageRequest);

        // then
        assertThat(reports.getContent()).isNotEmpty();
        assertThat(reports.getContent().get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("신고자별 신고 목록 조회")
    void findByReporterOrderByCreatedAtDesc_success() {
        // given
        PageRequest pageRequest = PageRequest.of(0, 10);

        // when
        Page<Report> reports = reportRepository.findByReporterOrderByCreatedAtDesc(reporter, pageRequest);

        // then
        assertThat(reports.getContent()).isNotEmpty();
        assertThat(reports.getContent().get(0).getReporter()).isEqualTo(reporter);
    }

    @Test
    @DisplayName("대상 타입 단독 필터로 관리자 신고 목록을 조회한다")
    void findAdminReports_filtersByTargetTypeOnly() {
        Report userTargetReport = Report.builder()
                .reporter(reporter)
                .targetType("USER")
                .targetId(2L)
                .reasonType("ABUSE")
                .remark("User report")
                .build();
        entityManager.persist(userTargetReport);
        entityManager.flush();

        Page<Report> reports = reportRepository.findAdminReports(null, "USER", PageRequest.of(0, 10));

        assertThat(reports.getContent()).hasSize(1);
        assertThat(reports.getContent().get(0).getTargetType()).isEqualTo("USER");
    }

    @Test
    @DisplayName("상태 단독 필터로 관리자 신고 목록을 조회한다")
    void findAdminReports_filtersByStatusOnly() {
        Page<Report> reports = reportRepository.findAdminReports("PENDING", null, PageRequest.of(0, 10));

        assertThat(reports.getContent()).hasSize(1);
        assertThat(reports.getContent().get(0).getStatus()).isEqualTo("PENDING");
    }

    @Test
    @DisplayName("상태와 대상 타입 복합 필터로 관리자 신고 목록을 조회한다")
    void findAdminReports_filtersByStatusAndTargetType() {
        Page<Report> reports = reportRepository.findAdminReports("PENDING", "POST", PageRequest.of(0, 10));

        assertThat(reports.getContent()).hasSize(1);
        assertThat(reports.getContent().get(0).getStatus()).isEqualTo("PENDING");
        assertThat(reports.getContent().get(0).getTargetType()).isEqualTo("POST");
    }

    @Test
    @DisplayName("필터 없이 관리자 신고 목록을 조회한다")
    void findAdminReports_withoutFilters() {
        Page<Report> reports = reportRepository.findAdminReports(null, null, PageRequest.of(0, 10));

        assertThat(reports.getContent()).hasSize(1);
    }
}
