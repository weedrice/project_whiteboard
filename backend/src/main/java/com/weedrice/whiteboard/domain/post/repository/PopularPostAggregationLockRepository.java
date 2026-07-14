package com.weedrice.whiteboard.domain.post.repository;

import jakarta.persistence.EntityManager;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

@Repository
@RequiredArgsConstructor
public class PopularPostAggregationLockRepository {

    static final long LOCK_KEY = 0x4E4F564949530001L;

    private final EntityManager entityManager;

    public boolean tryAcquireTransactionLock() {
        Object result = entityManager.createNativeQuery("SELECT pg_try_advisory_xact_lock(:lockKey)")
                .setParameter("lockKey", LOCK_KEY)
                .getSingleResult();
        return Boolean.TRUE.equals(result);
    }
}
