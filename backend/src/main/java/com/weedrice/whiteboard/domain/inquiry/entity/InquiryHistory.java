package com.weedrice.whiteboard.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "inquiry_histories")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryHistory {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Column(name = "actor_user_id")
    private Long actorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "action_type", length = 40, nullable = false)
    private InquiryHistoryAction actionType;

    @Enumerated(EnumType.STRING)
    @Column(name = "from_status", length = 30)
    private InquiryStatus fromStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "to_status", length = 30, nullable = false)
    private InquiryStatus toStatus;

    @Column(name = "reason", length = 500)
    private String reason;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    public InquiryHistory(Long inquiryId, Long actorUserId, InquiryHistoryAction actionType,
                          InquiryStatus fromStatus, InquiryStatus toStatus, String reason, LocalDateTime now) {
        this.inquiryId = inquiryId;
        this.actorUserId = actorUserId;
        this.actionType = actionType;
        this.fromStatus = fromStatus;
        this.toStatus = toStatus;
        this.reason = reason;
        this.createdAt = now;
    }
}
