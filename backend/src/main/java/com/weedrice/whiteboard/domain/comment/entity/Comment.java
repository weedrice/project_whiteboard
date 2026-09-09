package com.weedrice.whiteboard.domain.comment.entity;

import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.domain.actor.AgentIdRef;
import com.weedrice.whiteboard.domain.actor.CommentIdRef;
import com.weedrice.whiteboard.domain.actor.PostIdRef;
import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_post", columnList = "post_id, is_deleted, created_at"),
        @Index(name = "idx_comments_user", columnList = "user_id, is_deleted"),
        @Index(name = "idx_comments_agent", columnList = "agent_id, is_deleted, created_at"),
        @Index(name = "idx_comments_parent", columnList = "parent_id")
})
public class Comment extends BaseTimeEntity implements CommentIdRef {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @Column(name = "post_id", nullable = false)
    private Long postId;

    @Column(name = "user_id", nullable = false)
    private Long userId;

    @Column(name = "agent_id")
    private Long agentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(name = "depth", nullable = false)
    private Integer depth;

    @Column(name = "content", nullable = false, columnDefinition = "TEXT")
    private String content;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_deleted", length = 1, nullable = false)
    private Boolean isDeleted;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_blinded", length = 1, nullable = false, columnDefinition = "varchar(1) default 'N'")
    private Boolean isBlinded;

    @Column(name = "blind_reason", length = 50)
    private String blindReason;

    @Column(name = "blinded_at")
    private LocalDateTime blindedAt;

    @Builder
    public Comment(Long postId, Long userId, Long agentId, Comment parent, Integer depth, String content) {
        this.postId = postId;
        this.userId = userId;
        this.agentId = agentId;
        this.parent = parent;
        this.depth = depth;
        this.content = content;
        this.isDeleted = false;
        this.likeCount = 0;
        this.isBlinded = false;
    }

    public static class CommentBuilder {
        public CommentBuilder post(PostIdRef post) {
            this.postId = post == null ? null : post.getPostId();
            return this;
        }

        public CommentBuilder user(UserIdRef user) {
            this.userId = user == null ? null : user.getUserId();
            return this;
        }

        public CommentBuilder agent(AgentIdRef agent) {
            this.agentId = agent == null ? null : agent.getAgentId();
            return this;
        }
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void deleteComment() {
        this.isDeleted = true;
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        this.likeCount--;
    }

    public void blind(String reason, LocalDateTime blindedAt) {
        this.isBlinded = true;
        this.blindReason = reason;
        this.blindedAt = blindedAt;
    }

    public void unblind() {
        this.isBlinded = false;
        this.blindReason = null;
        this.blindedAt = null;
    }
}
