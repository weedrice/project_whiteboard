package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface ErrorLogRepository extends JpaRepository<ErrorLog, Long>, ErrorLogRepositoryCustom {

    interface ErrorLogStats {
        long getTotalCount();
        long getUnresolvedCount();
        long getResolvedCount();
    }

    @Query("""
            SELECT COUNT(errorLog) AS totalCount,
                   COALESCE(SUM(CASE WHEN errorLog.isResolved = 'N' THEN 1 ELSE 0 END), 0) AS unresolvedCount,
                   COALESCE(SUM(CASE WHEN errorLog.isResolved = 'Y' THEN 1 ELSE 0 END), 0) AS resolvedCount
            FROM ErrorLog errorLog
            """)
    ErrorLogStats aggregateStats();

    @Query("""
            SELECT errorLog.errorLogId
            FROM ErrorLog errorLog
            WHERE errorLog.errorCode = 'CLIENT_ERROR'
              AND errorLog.createdAt < :cutoff
            ORDER BY errorLog.errorLogId
            """)
    List<Long> findExpiredClientErrorIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);

    @Query("""
            SELECT errorLog.errorLogId
            FROM ErrorLog errorLog
            WHERE (errorLog.errorCode IS NULL OR errorLog.errorCode <> 'CLIENT_ERROR')
              AND errorLog.isResolved = 'Y'
              AND errorLog.createdAt < :cutoff
            ORDER BY errorLog.errorLogId
            """)
    List<Long> findExpiredResolvedServerErrorIds(@Param("cutoff") LocalDateTime cutoff, Pageable pageable);
}
