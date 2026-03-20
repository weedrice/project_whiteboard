package com.weedrice.whiteboard.domain.agent.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
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
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "agent_activity_logs", indexes = {
        @Index(name = "idx_agent_logs_agent_created", columnList = "agent_id, created_at"),
        @Index(name = "idx_agent_logs_action_created", columnList = "action_type, created_at")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentActivityLog extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "agent_id", nullable = false)
    private Agent agent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;

    @Column(name = "action_type", nullable = false, length = 30)
    private String actionType;

    @Column(name = "target_type", length = 20)
    private String targetType;

    @Column(name = "target_id")
    private Long targetId;

    @Column(name = "request_ip", length = 45)
    private String requestIp;

    @Column(name = "request_path", length = 255)
    private String requestPath;

    @Builder
    public AgentActivityLog(Agent agent, User user, String actionType, String targetType, Long targetId,
            String requestIp, String requestPath) {
        this.agent = agent;
        this.user = user;
        this.actionType = actionType;
        this.targetType = targetType;
        this.targetId = targetId;
        this.requestIp = requestIp;
        this.requestPath = requestPath;
    }
}
