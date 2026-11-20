package com.weedrice.whiteboard.domain.post.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Embeddable;
import jakarta.persistence.EmbeddedId;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.OnDelete;
import org.hibernate.annotations.OnDeleteAction;

import java.io.Serializable;
import java.util.UUID;

@Entity
@Table(name = "view_history_sessions")
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ViewHistorySession {
    @Embeddable
    public record Id(@Column(name = "view_history_id") Long historyId,
                     @Column(name = "session_id") UUID sessionId) implements Serializable {}

    @EmbeddedId
    private Id id;

    @ManyToOne(fetch = FetchType.LAZY, optional = false)
    @JoinColumn(name = "view_history_id", insertable = false, updatable = false)
    @OnDelete(action = OnDeleteAction.CASCADE)
    private ViewHistory history;

    @Getter
    @Column(name = "duration_ms", nullable = false)
    private long durationMs;

    public ViewHistorySession(ViewHistory history, UUID sessionId) {
        this.history = history;
        this.id = new Id(history.getViewHistoryId(), sessionId);
    }

    public void recordDuration(long durationMs) {
        this.durationMs = durationMs;
    }
}
