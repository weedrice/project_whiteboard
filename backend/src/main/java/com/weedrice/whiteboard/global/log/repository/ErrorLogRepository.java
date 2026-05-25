package com.weedrice.whiteboard.global.log.repository;

import com.weedrice.whiteboard.global.log.entity.ErrorLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

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
}
