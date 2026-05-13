package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.config.QuerydslConfig;
import com.weedrice.whiteboard.global.log.dto.ErrorLogSearchRequest;
import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.boot.test.autoconfigure.orm.jpa.TestEntityManager;
import org.springframework.context.annotation.Import;
import org.springframework.data.domain.PageRequest;

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
                .extracting(ErrorLog::getErrorLogId)
                .containsExactly(second.getErrorLogId(), first.getErrorLogId());
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
