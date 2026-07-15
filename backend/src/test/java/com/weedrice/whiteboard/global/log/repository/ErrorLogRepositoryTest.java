package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.dto.ErrorLogResponse;
import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.boot.jpa.test.autoconfigure.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

import java.lang.reflect.Field;
import java.time.LocalDateTime;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(QuerydslConfig.class)
class ErrorLogRepositoryTest {

    @Autowired
    private TestEntityManager entityManager;

    @Autowired
    private ErrorLogRepository errorLogRepository;

    @Test
    @DisplayName("searchErrorLogs orders same createdAt by errorLogId descending")
    void searchErrorLogs_ordersByErrorLogIdDescWhenCreatedAtTies() {
        ErrorLog first = persistErrorLog("ERROR1");
        ErrorLog second = persistErrorLog("ERROR2");
        LocalDateTime sameCreatedAt = LocalDateTime.of(2026, 5, 13, 12, 0);
        updateCreatedAt(first, sameCreatedAt);
        updateCreatedAt(second, sameCreatedAt);
        entityManager.clear();

        var result = errorLogRepository.searchErrorLogs(new ErrorLogSearchRequest(), PageRequest.of(0, 10));

        assertThat(result.getContent())
                .extracting(resultItem -> resultItem.getErrorLogId())
                .containsExactly(second.getErrorLogId(), first.getErrorLogId());
    }

    @Test
    @DisplayName("searchErrorLogs excludes stackTrace from list projection")
    void searchErrorLogs_excludesStackTraceFromListProjection() {
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode("ERROR1")
                .errorType("RuntimeException")
                .httpStatus(500)
                .message("Error")
                .requestUri("/api/v1/test")
                .requestMethod("GET")
                .ipAddress("127.0.0.1")
                .stackTrace("stack trace")
                .build();
        entityManager.persistAndFlush(errorLog);
        entityManager.clear();

        var result = errorLogRepository.searchErrorLogs(new ErrorLogSearchRequest(), PageRequest.of(0, 10));

        assertThat(result.getContent().get(0).getErrorLogId()).isEqualTo(errorLog.getErrorLogId());
        assertThat(ErrorLogResponse.ErrorLogSummary.class.getDeclaredFields())
                .extracting(Field::getName)
                .doesNotContain("stackTrace");
        assertThat(result.getContent().get(0))
                .hasNoNullFieldsOrPropertiesExcept("errorCode", "userId", "userAgent", "resolvedBy",
                        "resolvedAt", "resolvedMemo");
    }

    @Test
    @DisplayName("aggregateStats returns total unresolved and resolved counts in one query")
    void aggregateStats_returnsCounts() {
        persistErrorLog("ERROR1");
        ErrorLog resolved = persistErrorLog("ERROR2");
        resolved.resolve(1L, "done", LocalDateTime.of(2026, 5, 13, 12, 5));
        entityManager.flush();
        entityManager.clear();

        ErrorLogRepository.ErrorLogStats stats = errorLogRepository.aggregateStats();

        assertThat(stats.getTotalCount()).isEqualTo(2L);
        assertThat(stats.getUnresolvedCount()).isEqualTo(1L);
        assertThat(stats.getResolvedCount()).isEqualTo(1L);
    }

    @Test
    @DisplayName("aggregateStats returns zero counts for an empty table")
    void aggregateStats_empty_returnsZeroCounts() {
        ErrorLogRepository.ErrorLogStats stats = errorLogRepository.aggregateStats();

        assertThat(stats.getTotalCount()).isZero();
        assertThat(stats.getUnresolvedCount()).isZero();
        assertThat(stats.getResolvedCount()).isZero();
    }

    private ErrorLog persistErrorLog(String errorCode) {
        ErrorLog errorLog = ErrorLog.builder()
                .errorCode(errorCode)
                .errorType("RuntimeException")
                .httpStatus(500)
                .message("Error")
                .requestUri("/api/v1/test")
                .requestMethod("GET")
                .ipAddress("127.0.0.1")
                .build();
        return entityManager.persistAndFlush(errorLog);
    }

    private void updateCreatedAt(ErrorLog errorLog, LocalDateTime createdAt) {
        entityManager.getEntityManager()
                .createNativeQuery("UPDATE error_logs SET created_at = :createdAt WHERE error_log_id = :errorLogId")
                .setParameter("createdAt", createdAt)
                .setParameter("errorLogId", errorLog.getErrorLogId())
                .executeUpdate();
    }
}
