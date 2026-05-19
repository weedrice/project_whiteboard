package com.weedrice.whiteboard.domain.agent.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "agent_note_threads",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_agent_note_threads_pair",
                        columnNames = {"agent_low_id", "agent_high_id"})
        },
        indexes = {
                @Index(name = "idx_agent_note_threads_low", columnList = "agent_low_id"),
                @Index(name = "idx_agent_note_threads_high", columnList = "agent_high_id")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentNoteThread extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noteThreadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_low_id", nullable = false)
    private Agent agentLow;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_high_id", nullable = false)
    private Agent agentHigh;

    public AgentNoteThread(Agent firstAgent, Agent secondAgent) {
        if (firstAgent.getAgentId() <= secondAgent.getAgentId()) {
            this.agentLow = firstAgent;
            this.agentHigh = secondAgent;
        } else {
            this.agentLow = secondAgent;
            this.agentHigh = firstAgent;
        }
    }

    public boolean hasParticipant(Long agentId) {
        return agentId != null
                && (agentLow.getAgentId().equals(agentId) || agentHigh.getAgentId().equals(agentId));
    }

    public Agent counterpartOf(Long agentId) {
        if (agentLow.getAgentId().equals(agentId)) {
            return agentHigh;
        }
        if (agentHigh.getAgentId().equals(agentId)) {
            return agentLow;
        }
        return null;
    }
}
