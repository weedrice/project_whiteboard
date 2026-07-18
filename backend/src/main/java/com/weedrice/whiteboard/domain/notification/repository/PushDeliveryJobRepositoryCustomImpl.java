package com.weedrice.whiteboard.domain.notification.repository;

import com.weedrice.whiteboard.domain.notification.entity.PushDeliveryJob;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.UUID;
import javax.sql.DataSource;
import java.sql.SQLException;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

public class PushDeliveryJobRepositoryCustomImpl implements PushDeliveryJobRepositoryCustom {

    private static final String INSERT_SQL = """
            INSERT INTO push_delivery_jobs (
                event_id, subscription_id, receiver_user_id, endpoint, p256dh, auth,
                subscription_modified_at, payload, status, retry_count, next_attempt_at,
                created_at, modified_at)
            VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?, ?)
            """;
    private static final ConcurrentMap<JobIdentity, H2JobLock> H2_JOB_LOCKS = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;
    private final boolean h2Database;

    public PushDeliveryJobRepositoryCustomImpl(JdbcTemplate jdbcTemplate, DataSource dataSource) {
        this.jdbcTemplate = jdbcTemplate;
        try (var connection = dataSource.getConnection()) {
            h2Database = "H2".equalsIgnoreCase(connection.getMetaData().getDatabaseProductName());
        } catch (SQLException exception) {
            throw new IllegalStateException("Unable to determine push delivery job database", exception);
        }
    }

    @Override
    public boolean insertIfAbsent(PushDeliveryJob job) {
        if (h2Database) {
            return insertIfAbsentForH2(job);
        }
        return jdbcTemplate.update(INSERT_SQL + " ON CONFLICT (event_id, subscription_id) DO NOTHING",
                arguments(job)) == 1;
    }

    private boolean insertIfAbsentForH2(PushDeliveryJob job) {
        requireTransaction();
        JobIdentity identity = new JobIdentity(job.getEventId(), job.getSubscriptionId());
        H2JobLock jobLock = H2_JOB_LOCKS.compute(identity, (ignored, existing) -> {
            H2JobLock resolved = existing == null ? new H2JobLock() : existing;
            resolved.references++;
            return resolved;
        });
        jobLock.lock.lock();
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                jobLock.lock.unlock();
                H2_JOB_LOCKS.computeIfPresent(identity, (ignored, existing) -> {
                    existing.references--;
                    return existing.references == 0 ? null : existing;
                });
            }
        });

        Integer existing = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM push_delivery_jobs WHERE event_id = ? AND subscription_id = ?",
                Integer.class,
                job.getEventId(),
                job.getSubscriptionId());
        return existing != null && existing == 0 && jdbcTemplate.update(INSERT_SQL, arguments(job)) == 1;
    }

    private Object[] arguments(PushDeliveryJob job) {
        return new Object[] {
                job.getEventId(), job.getSubscriptionId(), job.getReceiverUserId(), job.getEndpoint(),
                job.getP256dh(), job.getAuth(), job.getSubscriptionModifiedAt(), job.getPayload(),
                job.getStatus().name(), job.getRetryCount(), job.getNextAttemptAt(),
                job.getNextAttemptAt(), job.getNextAttemptAt()
        };
    }

    private void requireTransaction() {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Push delivery job insert requires an active transaction");
        }
    }

    private record JobIdentity(UUID eventId, Long subscriptionId) {
    }

    private static final class H2JobLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
