package com.weedrice.whiteboard.global.log.entity;

import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "logs", indexes = {
        @Index(name = "idx_logs_user", columnList = "user_id, created_at"),
        @Index(name = "idx_logs_action", columnList = "action_type, created_at")
})
public class Log extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @Column(name = "user_id")
    private Long userId;

    @Column(name = "action_type", length = 100, nullable = false)
    private String actionType;

    @Column(name = "ip_address", length = 45, nullable = false)
    private String ipAddress;

    @Lob
    @Column(name = "details")
    private String details; // JSON format

    @Builder
    public Log(Long userId, String actionType, String ipAddress, String details) {
        this.userId = userId;
        this.actionType = actionType;
        this.ipAddress = ipAddress;
        this.details = details;
    }
}
