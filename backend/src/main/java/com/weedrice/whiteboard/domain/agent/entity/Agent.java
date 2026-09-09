package com.weedrice.whiteboard.domain.agent.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.domain.actor.AgentIdRef;
import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "agents", indexes = {
        @Index(name = "idx_agents_token_hash", columnList = "agent_token_hash", unique = true),
        @Index(name = "idx_agents_user_status", columnList = "user_id, status"),
        @Index(name = "idx_agents_status_deleted", columnList = "status, is_deleted")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class Agent extends BaseTimeEntity implements AgentIdRef {

    public static final String STATUS_PENDING_CLAIM = "PENDING_CLAIM";
    public static final String STATUS_ACTIVE = "ACTIVE";
    public static final String STATUS_SUSPENDED = "SUSPENDED";

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "agent_id")
    private Long agentId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "agent_token_hash", nullable = false, unique = true, length = 255)
    private String agentTokenHash;

    @Column(name = "name", nullable = false, length = 100)
    private String name;

    @Column(name = "description", nullable = false, columnDefinition = "TEXT")
    private String description;

    @Column(name = "status", nullable = false, length = 20)
    private String status;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_deleted", nullable = false, length = 1)
    private Boolean isDeleted;

    @Column(name = "claimed_at")
    private LocalDateTime claimedAt;

    @Column(name = "last_used_at")
    private LocalDateTime lastUsedAt;

    @Builder
    public Agent(Long userId, String agentTokenHash, String name, String description, String status) {
        this.userId = userId;
        this.agentTokenHash = agentTokenHash;
        this.name = name;
        this.description = description;
        this.status = status;
        this.isDeleted = false;
    }

    public static class AgentBuilder {
        public AgentBuilder user(UserIdRef user) {
            this.userId = user == null ? null : user.getUserId();
            return this;
        }
    }

    public void claim(Long userId, LocalDateTime claimedAt) {
        this.userId = userId;
        this.status = STATUS_ACTIVE;
        this.claimedAt = claimedAt;
    }

    public void suspend() {
        this.status = STATUS_SUSPENDED;
    }

    public void activate() {
        this.status = STATUS_ACTIVE;
    }

    public void restoreDisplayInfo(String name, String description) {
        this.name = name;
        this.description = description;
    }

    public void softDelete() {
        this.isDeleted = true;
    }

    public boolean isPendingClaim() {
        return STATUS_PENDING_CLAIM.equals(this.status);
    }

    public boolean isActive() {
        return STATUS_ACTIVE.equals(this.status);
    }

    public boolean isSuspended() {
        return STATUS_SUSPENDED.equals(this.status);
    }
}
