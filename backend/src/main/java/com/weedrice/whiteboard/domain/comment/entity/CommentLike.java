package com.weedrice.whiteboard.domain.comment.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import com.weedrice.whiteboard.domain.actor.UserIdRef;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comment_likes")
@IdClass(CommentLikeId.class)
public class CommentLike extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id")
    private Comment comment;

    @Builder
    public CommentLike(Long userId, Comment comment) {
        this.userId = userId;
        this.comment = comment;
    }

    public static class CommentLikeBuilder {
        public CommentLikeBuilder user(UserIdRef user) {
            this.userId = user == null ? null : user.getUserId();
            return this;
        }
    }
}
