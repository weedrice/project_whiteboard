package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.NotificationDeliveryJob;
import org.springframework.dao.DuplicateKeyException;
import org.springframework.jdbc.core.JdbcTemplate;

import javax.sql.DataSource;
import java.sql.SQLException;
import java.time.LocalDateTime;

public class NotificationDeliveryJobRepositoryCustomImpl implements NotificationDeliveryJobRepositoryCustom {

    private final JdbcTemplate jdbcTemplate;
    private final boolean postgreSql;

    public NotificationDeliveryJobRepositoryCustomImpl(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        try (var connection = dataSource.getConnection()) {
            this.postgreSql = connection.getMetaData().getDatabaseProductName()
                    .toLowerCase(java.util.Locale.ROOT).contains("postgresql");
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to determine notification job database", exception);
        }
    }

    @Override
    public int insertIfAbsent(NotificationDeliveryJob job, LocalDateTime now) {
        String suffix = postgreSql ? " ON CONFLICT (event_id) DO NOTHING" : "";
        try {
            return jdbcTemplate.update("""
                    INSERT INTO notification_delivery_jobs (
                        event_id, receiver_user_id, actor_user_id, actor_agent_id,
                        notification_type, source_type, source_id, content, message_key, message_params,
                        status, retry_count, next_attempt_at, processing_started_at, last_error,
                        failure_code, first_failed_at, last_failed_at, redrive_count, created_at, modified_at
                    ) VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
                    """ + suffix,
                    job.getEventId(), job.getReceiverUserId(), job.getActorUserId(), job.getActorAgentId(),
                    job.getNotificationType().name(), job.getSourceType().name(), job.getSourceId(),
                    job.getContent(), job.getMessageKey(), job.getMessageParams(), job.getStatus().name(),
                    job.getRetryCount(), job.getNextAttemptAt(), job.getProcessingStartedAt(), job.getLastError(),
                    job.getFailureCode(), job.getFirstFailedAt(), job.getLastFailedAt(), job.getRedriveCount(),
                    now, now);
        } catch (DuplicateKeyException duplicate) {
            return 0;
        }
    }

    @Override
    public int deleteCompletedBatch(LocalDateTime cutoff, int batchSize) {
        return deleteTerminalBatch("COMPLETED", "modified_at", cutoff, batchSize);
    }

    @Override
    public int deleteFailedBatch(LocalDateTime cutoff, int batchSize) {
        return deleteTerminalBatch("FAILED", "last_failed_at", cutoff, batchSize);
    }

    private int deleteTerminalBatch(String status, String cutoffColumn, LocalDateTime cutoff, int batchSize) {
        if (batchSize < 1) {
            throw new IllegalArgumentException("batchSize must be positive");
        }
        if (postgreSql) {
            return jdbcTemplate.update("""
                    WITH candidates AS (
                        SELECT job_id
                        FROM notification_delivery_jobs
                        WHERE status = ?
                          AND %s < ?
                        ORDER BY %s ASC, job_id ASC
                        FOR UPDATE SKIP LOCKED
                        LIMIT ?
                    )
                    DELETE FROM notification_delivery_jobs job
                    USING candidates candidate
                    WHERE job.job_id = candidate.job_id
                      AND job.status = ?
                      AND job.%s < ?
                    """.formatted(cutoffColumn, cutoffColumn, cutoffColumn),
                    status, cutoff, batchSize, status, cutoff);
        }
        return jdbcTemplate.update("""
                DELETE FROM notification_delivery_jobs
                WHERE job_id IN (
                    SELECT job_id
                    FROM notification_delivery_jobs
                    WHERE status = ?
                      AND %s < ?
                    ORDER BY %s ASC, job_id ASC
                    LIMIT ?
                )
                  AND status = ?
                  AND %s < ?
                """.formatted(cutoffColumn, cutoffColumn, cutoffColumn),
                status, cutoff, batchSize, status, cutoff);
    }

}
