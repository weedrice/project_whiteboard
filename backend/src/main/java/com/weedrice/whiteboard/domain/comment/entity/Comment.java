package com.weedrice.whiteboard.domain.comment.entity;

import com.weedrice.whiteboard.domain.post.entity.Post;
import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comments", indexes = {
        @Index(name = "idx_comments_post", columnList = "post_id, is_deleted, created_at"),
        @Index(name = "idx_comments_user", columnList = "user_id, is_deleted"),
        @Index(name = "idx_comments_parent", columnList = "parent_id")
})
public class Comment extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_id")
    private Long commentId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "parent_id")
    private Comment parent;

    @Column(name = "depth", nullable = false)
    private Integer depth;

    @Lob
    @Column(name = "content", nullable = false)
    private String content;

    @Column(name = "is_deleted", length = 1, nullable = false)
    private String isDeleted;

    @Column(name = "like_count", nullable = false)
    private Integer likeCount;

    @Builder
    public Comment(Post post, User user, Comment parent, Integer depth, String content) {
        this.post = post;
        this.user = user;
        this.parent = parent;
        this.depth = depth;
        this.content = content;
        this.isDeleted = "N";
        this.likeCount = 0;
    }

    public void updateContent(String content) {
        this.content = content;
    }

    public void deleteComment() {
        this.isDeleted = "Y";
    }

    public void incrementLikeCount() {
        this.likeCount++;
    }

    public void decrementLikeCount() {
        this.likeCount--;
    }
}
