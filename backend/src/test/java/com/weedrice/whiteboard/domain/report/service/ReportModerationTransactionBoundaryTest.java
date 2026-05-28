package com.weedrice.whiteboard.domain.report.service;

import com.weedrice.whiteboard.domain.report.repository.ReportRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;

class ReportModerationTransactionBoundaryTest {

    @Test
    @DisplayName("processReport facades suspend ambient readOnly transactions")
    void processReportFacades_suspendTransactions() throws Exception {
        assertThat(transactional(ReportService.class.getMethod(
                "processReport", Long.class, Long.class, String.class, String.class)).propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
        assertThat(transactional(ReportModerationService.class.getMethod(
                "processReport", Long.class, Long.class, String.class, String.class)).propagation())
                .isEqualTo(Propagation.NOT_SUPPORTED);
    }

    @Test
    @DisplayName("command and read services keep separate transactional roles")
    void commandAndReadServices_defineSeparateTransactions() throws Exception {
        assertThat(ReportModerationCommandService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(transactional(ReportModerationCommandService.class.getMethod(
                "processReport", Long.class, Long.class, String.class, String.class)).readOnly()).isFalse();

        assertThat(ReportModerationReadService.class.getAnnotation(Transactional.class).readOnly()).isTrue();
        assertThat(ReportModerationReadService.class.getMethod("getAdminReportResponse", Long.class)
                .getAnnotation(Transactional.class)).isNull();
    }

    @Test
    @DisplayName("read response lookup fetches report writer associations")
    void findByReportId_fetchesAssemblerAssociations() throws Exception {
        Method method = ReportRepository.class.getMethod("findByReportId", Long.class);

        assertThat(method.getReturnType()).isEqualTo(Optional.class);
        assertThat(method.getAnnotation(EntityGraph.class).attributePaths())
                .containsExactly("reporter", "admin");
    }

    private Transactional transactional(Method method) {
        Transactional transactional = method.getAnnotation(Transactional.class);
        assertThat(transactional)
                .as("%s should declare @Transactional", method.getName())
                .isNotNull();
        return transactional;
    }
}
