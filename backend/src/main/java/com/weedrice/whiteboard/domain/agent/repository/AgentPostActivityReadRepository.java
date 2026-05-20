package com.weedrice.whiteboard.domain.agent.repository;

import com.weedrice.whiteboard.domain.agent.entity.AgentPostActivityRead;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface AgentPostActivityReadRepository extends JpaRepository<AgentPostActivityRead, Long> {

    interface LastReadAtProjection {
        Long getPostId();

        LocalDateTime getLastReadAt();
    }

    @Query("""
            SELECT read
            FROM AgentPostActivityRead read
            WHERE read.agent.agentId = :agentId
              AND read.post.postId = :postId
            """)
    Optional<AgentPostActivityRead> findByAgentIdAndPostId(
            @Param("agentId") Long agentId,
            @Param("postId") Long postId);

    @Modifying
    @Query(value = """
            INSERT INTO agent_post_activity_reads (
                agent_id,
                post_id,
                last_read_at,
                created_at,
                modified_at
            )
            VALUES (
                :agentId,
                :postId,
                :lastReadAt,
                CURRENT_TIMESTAMP,
                CURRENT_TIMESTAMP
            )
            ON CONFLICT ON CONSTRAINT uk_agent_post_activity_reads_agent_post
            DO UPDATE SET
                last_read_at = GREATEST(agent_post_activity_reads.last_read_at, EXCLUDED.last_read_at),
                modified_at = CURRENT_TIMESTAMP
            """, nativeQuery = true)
    int upsertLastReadAt(
            @Param("agentId") Long agentId,
            @Param("postId") Long postId,
            @Param("lastReadAt") LocalDateTime lastReadAt);

    @Query("""
            SELECT read.post.postId AS postId,
                   read.lastReadAt AS lastReadAt
            FROM AgentPostActivityRead read
            WHERE read.agent.agentId = :agentId
              AND read.post.postId IN :postIds
            """)
    List<LastReadAtProjection> findLastReadAtByAgentIdAndPostIds(
            @Param("agentId") Long agentId,
            @Param("postIds") List<Long> postIds);
}
