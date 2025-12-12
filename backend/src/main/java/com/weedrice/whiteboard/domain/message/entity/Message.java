package com.weedrice.whiteboard.domain.message.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "messages", indexes = {
        @Index(name = "idx_messages_receiver", columnList = "receiver_id, is_deleted_by_receiver, is_read, created_at DESC"),
        @Index(name = "idx_messages_sender", columnList = "sender_id, is_deleted_by_sender, created_at DESC")
})
public class Message extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "message_id")
    private Long messageId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_id", nullable = false)
    private User sender;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_id", nullable = false)
    private User receiver;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_read", length = 1, nullable = false)
    private Boolean isRead;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_deleted_by_sender", length = 1, nullable = false)
    private Boolean isDeletedBySender;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_deleted_by_receiver", length = 1, nullable = false)
    private Boolean isDeletedByReceiver;

    @Builder
    public Message(User sender, User receiver, String content) {
        this.sender = sender;
        this.receiver = receiver;
        this.content = content;
        this.isRead = false;
        this.isDeletedBySender = false;
        this.isDeletedByReceiver = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }

    public void deleteBySender() {
        this.isDeletedBySender = true;
    }

    public void deleteByReceiver() {
        this.isDeletedByReceiver = true;
    }
}
