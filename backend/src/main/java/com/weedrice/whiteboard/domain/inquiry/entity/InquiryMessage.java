package com.weedrice.whiteboard.domain.inquiry.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Getter
@Entity
@Table(name = "inquiry_messages")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class InquiryMessage {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @Column(name = "inquiry_id", nullable = false)
    private Long inquiryId;

    @Column(name = "author_user_id", nullable = false)
    private Long authorUserId;

    @Enumerated(EnumType.STRING)
    @Column(name = "message_type", length = 30, nullable = false)
    private InquiryMessageType messageType;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    public InquiryMessage(Long inquiryId, Long authorUserId, InquiryMessageType messageType,
                          String content, LocalDateTime now) {
        this.inquiryId = inquiryId;
        this.authorUserId = authorUserId;
        this.messageType = messageType;
        this.content = content;
        this.createdAt = now;
        this.modifiedAt = now;
    }
}
