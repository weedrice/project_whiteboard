package com.weedrice.whiteboard.domain.agent.entity;

import jakarta.persistence.Column;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import java.time.Instant;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

@Entity
@Table(name = "agent_write_operations")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentWriteOperation {
    @EmbeddedId
    private AgentWriteOperationId id;
    @Column(nullable = false, length = 32)
    private String action;
    @JdbcTypeCode(SqlTypes.CHAR)
    @Column(name = "request_digest", nullable = false, length = 64, columnDefinition = "char(64)")
    private String requestDigest;
    @Column(name = "response_json", nullable = false, columnDefinition = "text")
    private String responseJson;
    @Column(name = "completed_at", nullable = false)
    private Instant completedAt;

    public AgentWriteOperation(Long agentId, String operationId, String action,
            String requestDigest, String responseJson, Instant completedAt) {
        this.id = new AgentWriteOperationId(agentId, operationId);
        this.action = action;
        this.requestDigest = requestDigest;
        this.responseJson = responseJson;
        this.completedAt = completedAt;
    }
}
