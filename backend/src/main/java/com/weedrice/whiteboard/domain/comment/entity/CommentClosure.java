package com.weedrice.whiteboard.domain.comment.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Table(name = "comment_closures")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommentClosure {

    @EmbeddedId
    private CommentClosureId id;

    @MapsId("ancestorId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ancestor_id")
    private Comment ancestor;

    @MapsId("descendantId")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "descendant_id")
    private Comment descendant;

    @Column(name = "depth", nullable = false)
    private Integer depth;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @Builder
    public CommentClosure(Comment ancestor, Comment descendant, Integer depth) {
        this.id = new CommentClosureId(ancestor.getCommentId(), descendant.getCommentId());
        this.ancestor = ancestor;
        this.descendant = descendant;
        this.depth = depth;
    }
}
