package com.weedrice.whiteboard.domain.auth.repository;

import com.weedrice.whiteboard.domain.auth.entity.VerificationPurpose;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import org.springframework.jdbc.core.ConnectionCallback;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.stereotype.Repository;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.locks.ReentrantLock;

@Repository
@RequiredArgsConstructor
public class VerificationSendLockRepository {

    private static final String LOCK_NAMESPACE = "verification-send:";
    private static final ConcurrentMap<String, H2SendLock> H2_SEND_LOCKS = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    public void lock(String email, VerificationPurpose purpose) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Verification send lock requires an active transaction");
        }

        String lockKey = LOCK_NAMESPACE + purpose.name() + ":" + email;
        if (h2Database) {
            lockForH2(lockKey);
            return;
        }

        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                resultSet -> null,
                lockKey);
    }

    private void lockForH2(String lockKey) {
        H2SendLock sendLock = H2_SEND_LOCKS.compute(lockKey, (ignored, existing) -> {
            H2SendLock resolved = existing == null ? new H2SendLock() : existing;
            resolved.references++;
            return resolved;
        });
        sendLock.lock.lock();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                unlockForH2(lockKey, sendLock);
            }
        });
    }

    private void unlockForH2(String lockKey, H2SendLock sendLock) {
        sendLock.lock.unlock();
        H2_SEND_LOCKS.computeIfPresent(lockKey, (ignored, existing) -> {
            existing.references--;
            return existing.references == 0 ? null : existing;
        });
    }

    private static final class H2SendLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
