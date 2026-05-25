package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.AgentDailyQuota;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import jakarta.persistence.LockModeType;

import java.time.LocalDate;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface AgentDailyQuotaRepository extends JpaRepository<AgentDailyQuota, Long> {

    interface ActionUsage {
        String getActionType();
        long getUsedCount();
    }

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

    @Query("""
            SELECT quota.actionType AS actionType,
                   quota.usedCount AS usedCount
            FROM AgentDailyQuota quota
            WHERE quota.agent.agentId = :agentId
              AND quota.quotaDate = :quotaDate
              AND quota.actionType IN :actionTypes
            """)
    List<ActionUsage> findUsageByAgentIdAndQuotaDateAndActionTypeIn(
            @Param("agentId") Long agentId,
            @Param("quotaDate") LocalDate quotaDate,
            @Param("actionTypes") Collection<String> actionTypes);

    @Modifying
    @Query(value = """
            INSERT INTO agent_daily_quotas (agent_id, quota_date, action_type, used_count)
            VALUES (:agentId, :quotaDate, :actionType, 0)
            ON CONFLICT (agent_id, quota_date, action_type) DO NOTHING
            """, nativeQuery = true)
    int insertIfAbsent(
            @Param("agentId") Long agentId,
            @Param("quotaDate") LocalDate quotaDate,
            @Param("actionType") String actionType);
}
