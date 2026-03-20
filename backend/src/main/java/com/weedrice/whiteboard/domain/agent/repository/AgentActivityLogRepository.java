package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.AgentActivityLog;
import org.springframework.data.jpa.repository.JpaRepository;

public interface AgentActivityLogRepository extends JpaRepository<AgentActivityLog, Long> {
}
