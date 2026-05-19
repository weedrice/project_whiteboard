package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.AgentNoteThread;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface AgentNoteThreadRepository extends JpaRepository<AgentNoteThread, Long> {

    @EntityGraph(attributePaths = {"agentLow", "agentLow.user", "agentHigh", "agentHigh.user"})
    @Query("SELECT t FROM AgentNoteThread t WHERE t.noteThreadId = :noteThreadId")
    Optional<AgentNoteThread> findByIdWithParticipants(@Param("noteThreadId") Long noteThreadId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @EntityGraph(attributePaths = {"agentLow", "agentHigh"})
    @Query("""
            SELECT t
            FROM AgentNoteThread t
            WHERE t.agentLow.agentId = :lowAgentId
              AND t.agentHigh.agentId = :highAgentId
            """)
    Optional<AgentNoteThread> findByAgentPairForUpdate(
            @Param("lowAgentId") Long lowAgentId,
            @Param("highAgentId") Long highAgentId);

    @Modifying
    @Query(value = """
            INSERT INTO agent_note_threads (
                agent_low_id,
                agent_high_id,
                created_at,
                modified_at
            )
            VALUES (
                :lowAgentId,
                :highAgentId,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT ON CONSTRAINT uk_agent_note_threads_pair DO NOTHING
            """, nativeQuery = true)
    int insertIgnorePair(
            @Param("lowAgentId") Long lowAgentId,
            @Param("highAgentId") Long highAgentId);

    @Query(value = """
            SELECT t.noteThreadId
            FROM AgentNoteThread t
            WHERE EXISTS (
                SELECT 1
                FROM AgentNote n
                WHERE n.thread = t
                  AND n.receiverAgent.agentId = :agentId
                  AND n.isHiddenByReceiver = false
            )
            ORDER BY (
                SELECT MAX(n2.createdAt)
                FROM AgentNote n2
                WHERE n2.thread = t
                  AND (
                      (n2.senderAgent.agentId = :agentId AND n2.isHiddenBySender = false)
                      OR (n2.receiverAgent.agentId = :agentId AND n2.isHiddenByReceiver = false)
                  )
            ) DESC, t.noteThreadId DESC
            """,
            countQuery = """
            SELECT COUNT(t)
            FROM AgentNoteThread t
            WHERE EXISTS (
                SELECT 1
                FROM AgentNote n
                WHERE n.thread = t
                  AND n.receiverAgent.agentId = :agentId
                  AND n.isHiddenByReceiver = false
            )
            """)
    Page<Long> findInboxThreadIds(@Param("agentId") Long agentId, Pageable pageable);

    @Query(value = """
            SELECT t.noteThreadId
            FROM AgentNoteThread t
            WHERE EXISTS (
                SELECT 1
                FROM AgentNote n
                WHERE n.thread = t
                  AND n.senderAgent.agentId = :agentId
                  AND n.isHiddenBySender = false
            )
            ORDER BY (
                SELECT MAX(n2.createdAt)
                FROM AgentNote n2
                WHERE n2.thread = t
                  AND (
                      (n2.senderAgent.agentId = :agentId AND n2.isHiddenBySender = false)
                      OR (n2.receiverAgent.agentId = :agentId AND n2.isHiddenByReceiver = false)
                  )
            ) DESC, t.noteThreadId DESC
            """,
            countQuery = """
            SELECT COUNT(t)
            FROM AgentNoteThread t
            WHERE EXISTS (
                SELECT 1
                FROM AgentNote n
                WHERE n.thread = t
                  AND n.senderAgent.agentId = :agentId
                  AND n.isHiddenBySender = false
            )
            """)
    Page<Long> findSentThreadIds(@Param("agentId") Long agentId, Pageable pageable);

    @Query(value = """
            SELECT t.noteThreadId
            FROM AgentNoteThread t
            WHERE EXISTS (
                SELECT 1
                FROM AgentNote n
                WHERE n.thread = t
                  AND n.receiverAgent.agentId = :agentId
                  AND n.isRead = false
                  AND n.isHiddenByReceiver = false
            )
            ORDER BY (
                SELECT MAX(n2.createdAt)
                FROM AgentNote n2
                WHERE n2.thread = t
                  AND n2.receiverAgent.agentId = :agentId
                  AND n2.isRead = false
                  AND n2.isHiddenByReceiver = false
            ) DESC, t.noteThreadId DESC
            """,
            countQuery = """
            SELECT COUNT(t)
            FROM AgentNoteThread t
            WHERE EXISTS (
                SELECT 1
                FROM AgentNote n
                WHERE n.thread = t
                  AND n.receiverAgent.agentId = :agentId
                  AND n.isRead = false
                  AND n.isHiddenByReceiver = false
            )
            """)
    Page<Long> findUnreadThreadIds(@Param("agentId") Long agentId, Pageable pageable);
}
