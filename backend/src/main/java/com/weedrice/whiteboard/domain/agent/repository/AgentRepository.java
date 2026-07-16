package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.Agent;
import com.weedrice.whiteboard.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.domain.Pageable;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentRepository extends JpaRepository<Agent, Long> {
    Optional<Agent> findByAgentTokenHashAndIsDeletedFalse(String agentTokenHash);

    @EntityGraph(attributePaths = { "user" })
    @Query("SELECT a FROM Agent a WHERE a.agentTokenHash = :agentTokenHash AND a.isDeleted = false")
    Optional<Agent> findByAgentTokenHashAndIsDeletedFalseForAuthentication(
            @Param("agentTokenHash") String agentTokenHash);

    @EntityGraph(attributePaths = { "user" })
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT a FROM Agent a WHERE a.agentTokenHash = :agentTokenHash AND a.isDeleted = false")
    Optional<Agent> findByAgentTokenHashAndIsDeletedFalseForUpdate(@Param("agentTokenHash") String agentTokenHash);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            UPDATE Agent a
            SET a.lastUsedAt = :lastUsedAt
            WHERE a.agentId = :agentId
              AND a.isDeleted = false
              AND (a.lastUsedAt IS NULL OR a.lastUsedAt <= :staleBefore)
            """)
    int updateLastUsedAtIfStale(
            @Param("agentId") Long agentId,
            @Param("lastUsedAt") LocalDateTime lastUsedAt,
            @Param("staleBefore") LocalDateTime staleBefore);

    boolean existsByAgentTokenHashAndIsDeletedFalse(String agentTokenHash);

    boolean existsByNameAndIsDeletedFalse(String name);

    @Query("""
            SELECT a.name
            FROM Agent a
            WHERE a.isDeleted = false
              AND (a.name = :baseName OR a.name LIKE CONCAT(:baseName, ' %'))
            """)
    List<String> findActiveNamesByBaseName(@Param("baseName") String baseName);

    @EntityGraph(attributePaths = { "user" })
    Optional<Agent> findByNameAndIsDeletedFalse(String name);

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

    @Query("""
            SELECT a.agentId
            FROM Agent a
            WHERE a.status = :status
              AND a.isDeleted = true
              AND a.user IS NULL
              AND a.claimedAt IS NULL
              AND a.modifiedAt < :purgeBefore
            ORDER BY a.modifiedAt ASC, a.agentId ASC
            """)
    List<Long> findPendingClaimPurgeCandidateIds(
            @Param("status") String status,
            @Param("purgeBefore") LocalDateTime purgeBefore,
            Pageable pageable);

    @Modifying(clearAutomatically = true, flushAutomatically = true)
    @Query("""
            DELETE FROM Agent a
            WHERE a.agentId IN :agentIds
              AND a.status = :status
              AND a.isDeleted = true
              AND a.user IS NULL
              AND a.claimedAt IS NULL
              AND a.modifiedAt < :purgeBefore
            """)
    int hardDeleteEligiblePendingClaims(
            @Param("agentIds") List<Long> agentIds,
            @Param("status") String status,
            @Param("purgeBefore") LocalDateTime purgeBefore);

    @Query("""
            SELECT COUNT(a)
            FROM Agent a
            WHERE a.status = :status
              AND a.isDeleted = true
              AND a.user IS NULL
              AND a.claimedAt IS NULL
              AND a.modifiedAt < :purgeBefore
            """)
    long countPendingClaimsEligibleForPurge(
            @Param("status") String status,
            @Param("purgeBefore") LocalDateTime purgeBefore);
}
