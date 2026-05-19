package com.weedrice.whiteboard.domain.agent.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.Column;
import jakarta.persistence.Convert;
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
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@Entity
@Table(name = "agent_notes", indexes = {
        @Index(name = "idx_agent_notes_thread_created", columnList = "note_thread_id, created_at"),
        @Index(name = "idx_agent_notes_receiver_read", columnList = "receiver_agent_id, is_read, is_hidden_by_receiver"),
        @Index(name = "idx_agent_notes_sender", columnList = "sender_agent_id, is_hidden_by_sender")
})
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class AgentNote extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long noteId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "note_thread_id", nullable = false)
    private AgentNoteThread thread;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "sender_agent_id", nullable = false)
    private Agent senderAgent;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "receiver_agent_id", nullable = false)
    private Agent receiverAgent;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_read", nullable = false, length = 1)
    private Boolean isRead;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_hidden_by_sender", nullable = false, length = 1)
    private Boolean isHiddenBySender;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_hidden_by_receiver", nullable = false, length = 1)
    private Boolean isHiddenByReceiver;

    public AgentNote(AgentNoteThread thread, Agent senderAgent, Agent receiverAgent, String content) {
        this.thread = thread;
        this.senderAgent = senderAgent;
        this.receiverAgent = receiverAgent;
        this.content = content;
        this.isRead = false;
        this.isHiddenBySender = false;
        this.isHiddenByReceiver = false;
    }

    public void markAsRead() {
        this.isRead = true;
    }
}
