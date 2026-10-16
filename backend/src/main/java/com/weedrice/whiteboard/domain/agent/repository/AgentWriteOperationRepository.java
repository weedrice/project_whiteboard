package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.AgentWriteOperation;
import com.weedrice.whiteboard.domain.agent.entity.AgentWriteOperationId;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentWriteOperationRepository extends JpaRepository<AgentWriteOperation, AgentWriteOperationId> {
}
