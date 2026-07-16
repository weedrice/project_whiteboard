package com.weedrice.whiteboard.domain.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDate;

@Getter
@Entity
@Table(name = "agent_daily_quotas",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_agent_daily_quotas_agent_date_action",
                        columnNames = {"agent_id", "quota_date", "action_type"})
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentDailyQuota {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agent_daily_quota_id")
    private Long agentDailyQuotaId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @Column(name = "quota_date", nullable = false)
    private LocalDate quotaDate;

    @Column(name = "action_type", nullable = false, length = 20)
    private String actionType;

    @Column(name = "used_count", nullable = false)
    private long usedCount;

    @Builder
    public AgentDailyQuota(Agent agent, LocalDate quotaDate, String actionType, Long usedCount) {
        this.agent = agent;
        this.quotaDate = quotaDate;
        this.actionType = actionType;
        this.usedCount = usedCount != null ? usedCount : 0L;
    }

    public void reserve() {
        this.usedCount++;
    }

    public boolean hasReachedLimit(long limit) {
        return usedCount >= limit;
    }
}
