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
@Table(name = "post_likes")
@IdClass(PostLikeId.class)
public class PostLike extends BaseTimeEntity {

    @Id
    @Column(name = "user_id")
    private Long userId;

    @Id
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id")
    private Post post;

    @Builder
    public PostLike(Long userId, Post post) {
        this.userId = userId;
        this.post = post;
    }

    public static class PostLikeBuilder {
        public PostLikeBuilder user(UserIdRef user) {
            this.userId = user == null ? null : user.getUserId();
            return this;
        }
    }
}
