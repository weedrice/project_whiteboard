package com.weedrice.whiteboard.domain.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import lombok.AllArgsConstructor;
import lombok.EqualsAndHashCode;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.io.Serializable;

@Embeddable
@Getter
@NoArgsConstructor
@AllArgsConstructor
@EqualsAndHashCode
public class AgentWriteOperationId implements Serializable {
    @Column(name = "agent_id", nullable = false)
    private Long agentId;
    @Column(name = "operation_id", nullable = false, length = 36)
    private String operationId;
}
