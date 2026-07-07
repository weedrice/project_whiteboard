package com.weedrice.whiteboard.domain.comment.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "comment_mentions",
        uniqueConstraints = @UniqueConstraint(name = "uk_comment_mentions_comment_user", columnNames = {"comment_id", "user_id"}),
        indexes = {
                @Index(name = "idx_comment_mentions_comment", columnList = "comment_id"),
                @Index(name = "idx_comment_mentions_user", columnList = "user_id")
        })
public class CommentMention extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "comment_mention_id")
    private Long commentMentionId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Builder
    public CommentMention(Comment comment, User user) {
        this.comment = comment;
        this.user = user;
    }
}
