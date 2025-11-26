package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.comment.entity.Comment;
import com.weedrice.whiteboard.domain.user.entity.User;
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
                @Index(name = "idx_view_histories_post", columnList = "post_id")
        })
public class ViewHistory extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "view_history_id")
    private Long viewHistoryId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "last_read_comment_id")
    private Comment lastReadComment;

    @Column(name = "duration_ms", nullable = false)
    private Long durationMs;

    @Builder
    public ViewHistory(User user, Post post) {
        this.user = user;
        this.post = post;
        this.durationMs = 0L;
    }

    public void updateView(Comment lastReadComment, long durationMs) {
        this.lastReadComment = lastReadComment;
        this.durationMs += durationMs;
    }
}
