package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.AgentDailyQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Optional;

public interface AgentDailyQuotaRepository extends JpaRepository<AgentDailyQuota, Long> {

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT quota
            FROM AgentDailyQuota quota
            WHERE quota.agent.agentId = :agentId
              AND quota.quotaDate = :quotaDate
              AND quota.actionType = :actionType
            """)
    Optional<AgentDailyQuota> findForUpdate(
            @Param("agentId") Long agentId,
            @Param("quotaDate") LocalDate quotaDate,
            @Param("actionType") String actionType);
}
