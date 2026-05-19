package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.AgentNote;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.EntityGraph;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Collection;
import java.util.List;

public interface AgentNoteRepository extends JpaRepository<AgentNote, Long> {

    interface ThreadUnreadCountProjection {
        Long getNoteThreadId();

        long getUnreadCount();
    }

    @EntityGraph(attributePaths = {"thread", "senderAgent", "receiverAgent"})
    @Query("""
            SELECT n
            FROM AgentNote n
            WHERE n.thread.noteThreadId = :noteThreadId
              AND (
                  (n.senderAgent.agentId = :agentId AND n.isHiddenBySender = false)
                  OR (n.receiverAgent.agentId = :agentId AND n.isHiddenByReceiver = false)
              )
            ORDER BY n.createdAt DESC, n.noteId DESC
            """)
    List<AgentNote> findLatestVisibleInThread(
            @Param("noteThreadId") Long noteThreadId,
            @Param("agentId") Long agentId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"thread", "senderAgent", "receiverAgent"})
    @Query("""
            SELECT n
            FROM AgentNote n
            WHERE n.thread.noteThreadId IN :noteThreadIds
              AND (
                  (n.senderAgent.agentId = :agentId AND n.isHiddenBySender = false)
                  OR (n.receiverAgent.agentId = :agentId AND n.isHiddenByReceiver = false)
              )
              AND NOT EXISTS (
                  SELECT 1
                  FROM AgentNote newer
                  WHERE newer.thread = n.thread
                    AND (
                        (newer.senderAgent.agentId = :agentId AND newer.isHiddenBySender = false)
                        OR (newer.receiverAgent.agentId = :agentId AND newer.isHiddenByReceiver = false)
                    )
                    AND (
                        newer.createdAt > n.createdAt
                        OR (newer.createdAt = n.createdAt AND newer.noteId > n.noteId)
                    )
              )
            ORDER BY n.createdAt DESC, n.noteId DESC
            """)
    List<AgentNote> findLatestVisibleInThreads(
            @Param("noteThreadIds") Collection<Long> noteThreadIds,
            @Param("agentId") Long agentId);

    @EntityGraph(attributePaths = {"senderAgent", "receiverAgent"})
    @Query(value = """
            SELECT n
            FROM AgentNote n
            WHERE n.thread.noteThreadId = :noteThreadId
              AND (
                  (n.senderAgent.agentId = :agentId AND n.isHiddenBySender = false)
                  OR (n.receiverAgent.agentId = :agentId AND n.isHiddenByReceiver = false)
              )
            ORDER BY n.createdAt ASC, n.noteId ASC
            """,
            countQuery = """
            SELECT COUNT(n)
            FROM AgentNote n
            WHERE n.thread.noteThreadId = :noteThreadId
              AND (
                  (n.senderAgent.agentId = :agentId AND n.isHiddenBySender = false)
                  OR (n.receiverAgent.agentId = :agentId AND n.isHiddenByReceiver = false)
              )
            """)
    Page<AgentNote> findVisibleThreadNotes(
            @Param("noteThreadId") Long noteThreadId,
            @Param("agentId") Long agentId,
            Pageable pageable);

    @EntityGraph(attributePaths = {"thread", "senderAgent", "receiverAgent"})
    List<AgentNote> findByNoteIdIn(Collection<Long> noteIds);

    @Query("""
            SELECT COUNT(n)
            FROM AgentNote n
            WHERE n.receiverAgent.agentId = :agentId
              AND n.isRead = false
              AND n.isHiddenByReceiver = false
            """)
    long countUnreadNotes(@Param("agentId") Long agentId);

    @Query("""
            SELECT COUNT(DISTINCT n.thread.noteThreadId)
            FROM AgentNote n
            WHERE n.receiverAgent.agentId = :agentId
              AND n.isRead = false
              AND n.isHiddenByReceiver = false
            """)
    long countUnreadThreads(@Param("agentId") Long agentId);

    @Query("""
            SELECT COUNT(n)
            FROM AgentNote n
            WHERE n.thread.noteThreadId = :noteThreadId
              AND n.receiverAgent.agentId = :agentId
              AND n.isRead = false
              AND n.isHiddenByReceiver = false
            """)
    long countUnreadNotesInThread(
            @Param("noteThreadId") Long noteThreadId,
            @Param("agentId") Long agentId);

    @Query("""
            SELECT n.thread.noteThreadId AS noteThreadId,
                   COUNT(n) AS unreadCount
            FROM AgentNote n
            WHERE n.thread.noteThreadId IN :noteThreadIds
              AND n.receiverAgent.agentId = :agentId
              AND n.isRead = false
              AND n.isHiddenByReceiver = false
            GROUP BY n.thread.noteThreadId
            """)
    List<ThreadUnreadCountProjection> countUnreadNotesByThreadIds(
            @Param("noteThreadIds") Collection<Long> noteThreadIds,
            @Param("agentId") Long agentId);

    @Modifying(flushAutomatically = true)
    @Query("""
            UPDATE AgentNote n
            SET n.isRead = true
            WHERE n.thread.noteThreadId = :noteThreadId
              AND n.receiverAgent.agentId = :agentId
              AND n.isRead = false
              AND n.isHiddenByReceiver = false
            """)
    int markThreadReceivedNotesRead(
            @Param("noteThreadId") Long noteThreadId,
            @Param("agentId") Long agentId);
}
