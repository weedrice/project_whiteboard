package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByAgentTokenHashAndIsDeletedFalse(String agentTokenHash);

    @EntityGraph(attributePaths = { "user" })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agent a WHERE a.agentTokenHash = :agentTokenHash AND a.isDeleted = false")
    Optional<Agent> findByAgentTokenHashAndIsDeletedFalseForUpdate(@Param("agentTokenHash") String agentTokenHash);

    boolean existsByAgentTokenHashAndIsDeletedFalse(String agentTokenHash);

    boolean existsByNameAndIsDeletedFalse(String name);

    @EntityGraph(attributePaths = { "user" })
    Optional<Agent> findByAgentIdAndIsDeletedFalse(Long agentId);

    @EntityGraph(attributePaths = { "user" })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agent a WHERE a.agentId = :agentId AND a.isDeleted = false")
    Optional<Agent> findByAgentIdForUpdate(@Param("agentId") Long agentId);

    List<Agent> findByUserAndIsDeletedFalseOrderByCreatedAtDesc(User user);

    @EntityGraph(attributePaths = { "user" })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Agent a
            WHERE a.user.userId = :userId
              AND a.isDeleted = false
            ORDER BY a.agentId ASC
            """)
    List<Agent> findByUserIdAndIsDeletedFalseForUpdateOrderByAgentIdAsc(@Param("userId") Long userId);

    boolean existsByAgentIdAndUserAndIsDeletedFalse(Long agentId, User user);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("""
            SELECT a
            FROM Agent a
            WHERE a.status = :status
              AND a.isDeleted = false
              AND a.user IS NULL
              AND a.claimedAt IS NULL
              AND a.createdAt < :expiresBefore
            """)
    List<Agent> findExpiredPendingClaimsForUpdate(
            @Param("status") String status,
            @Param("expiresBefore") LocalDateTime expiresBefore);
}
