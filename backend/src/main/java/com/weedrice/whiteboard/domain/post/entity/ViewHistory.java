package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "view_histories",
        uniqueConstraints = {
                @UniqueConstraint(name = "uk_view_histories_user_post", columnNames = {"user_id", "post_id"})
        },
        indexes = {
                @Index(name = "idx_view_histories_user", columnList = "user_id, created_at DESC"),
                @Index(name = "idx_view_histories_user_modified", columnList = "user_id, modified_at DESC"),
                @Index(name = "idx_view_histories_post", columnList = "post_id")
        })
public class ViewHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "view_history_id")
    private Long viewHistoryId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "last_read_comment_id")
    private Long lastReadCommentId;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Builder
    public ViewHistory(Long userId, Post post) {
        this.userId = userId;
        this.post = post;
        this.durationMs = 0L;
    }

    public ViewHistory(UserIdRef user, Post post) {
        this(user == null ? null : user.getUserId(), post);
    }

    public static class ViewHistoryBuilder {
        public ViewHistoryBuilder user(UserIdRef user) {
            this.userId = user == null ? null : user.getUserId();
            return this;
        }
    }

    public void updateView(Long lastReadCommentId, long durationMs) {
        if (shouldAdvanceLastReadComment(lastReadCommentId)) {
            this.lastReadCommentId = lastReadCommentId;
        }
        this.durationMs += durationMs;
    }

    private boolean shouldAdvanceLastReadComment(Long candidateCommentId) {
        if (candidateCommentId == null) {
            return false;
        }
        if (this.lastReadCommentId == null) {
            return true;
        }
        return candidateCommentId > this.lastReadCommentId;
    }
}
