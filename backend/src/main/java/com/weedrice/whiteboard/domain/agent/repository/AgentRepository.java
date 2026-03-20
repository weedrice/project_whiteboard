package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.user.entity.User;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByAgentTokenHashAndIsDeletedFalse(String agentTokenHash);

    boolean existsByAgentTokenHashAndIsDeletedFalse(String agentTokenHash);

    @EntityGraph(attributePaths = { "user" })
    Optional<Agent> findByAgentIdAndIsDeletedFalse(Long agentId);

    List<Agent> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(User user);

    boolean existsByAgentIdAndUserAndIsDeletedFalse(Long agentId, User user);
}
