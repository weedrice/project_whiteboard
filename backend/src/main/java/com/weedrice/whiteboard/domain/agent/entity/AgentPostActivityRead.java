package com.weedrice.whiteboard.domain.agent.entity;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "agent_post_activity_reads",
        uniqueConstraints = {
                @UniqueConstraint(
                        name = "uk_agent_post_activity_reads_agent_post",
                        columnNames = {"agent_id", "post_id"})
        },
        indexes = {
                @Index(name = "idx_agent_post_activity_reads_lookup", columnList = "agent_id, post_id"),
                @Index(name = "idx_agent_post_activity_reads_post", columnList = "post_id, last_read_at")
        })
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentPostActivityRead extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agent_post_activity_read_id")
    private Long agentPostActivityReadId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "last_read_at", nullable = false)
    private LocalDateTime lastReadAt;

    @Builder
    public AgentPostActivityRead(Agent agent, Post post, LocalDateTime lastReadAt) {
        this.agent = agent;
        this.post = post;
        this.lastReadAt = lastReadAt;
    }

    public void markReadAt(LocalDateTime lastReadAt) {
        this.lastReadAt = lastReadAt;
    }
}
