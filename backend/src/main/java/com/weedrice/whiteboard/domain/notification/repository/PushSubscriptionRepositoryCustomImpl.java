package com.weedrice.whiteboard.domain.notification.repository;

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
public class PushSubscriptionRepositoryCustomImpl implements PushSubscriptionRepositoryCustom {

    private static final ConcurrentMap<String, H2EndpointLock> H2_ENDPOINT_LOCKS = new ConcurrentHashMap<>();

    private final JdbcTemplate jdbcTemplate;
    private boolean h2Database;

    @PostConstruct
    void initializeDatabaseDialect() {
        String databaseProductName = jdbcTemplate.execute(
                (ConnectionCallback<String>) connection -> connection.getMetaData().getDatabaseProductName());
        h2Database = "H2".equalsIgnoreCase(databaseProductName);
    }

    @Override
    public void lockEndpoint(String endpoint) {
        if (!TransactionSynchronizationManager.isActualTransactionActive()
                || !TransactionSynchronizationManager.isSynchronizationActive()) {
            throw new IllegalStateException("Push endpoint lock requires an active transaction");
        }

        if (h2Database) {
            lockEndpointForH2(endpoint);
            return;
        }

        jdbcTemplate.query(
                "SELECT pg_advisory_xact_lock(hashtextextended(?, 0))",
                resultSet -> null,
                endpoint);
    }

    private void lockEndpointForH2(String endpoint) {
        H2EndpointLock endpointLock = H2_ENDPOINT_LOCKS.compute(endpoint, (ignored, existing) -> {
            H2EndpointLock resolved = existing == null ? new H2EndpointLock() : existing;
            resolved.references++;
            return resolved;
        });
        endpointLock.lock.lock();

        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCompletion(int status) {
                unlockEndpoint(endpoint, endpointLock);
            }
        });
    }

    private void unlockEndpoint(String endpoint, H2EndpointLock endpointLock) {
        endpointLock.lock.unlock();
        H2_ENDPOINT_LOCKS.computeIfPresent(endpoint, (ignored, existing) -> {
            existing.references--;
            return existing.references == 0 ? null : existing;
        });
    }

    private static final class H2EndpointLock {
        private final ReentrantLock lock = new ReentrantLock();
        private int references;
    }
}
