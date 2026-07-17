package com.weedrice.whiteboard.global.lock;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface DomainLockRepository extends JpaRepository<DomainLock, String> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT lockEntry FROM DomainLock lockEntry WHERE lockEntry.lockName = :lockName")
    Optional<DomainLock> findByNameForUpdate(@Param("lockName") String lockName);
}
