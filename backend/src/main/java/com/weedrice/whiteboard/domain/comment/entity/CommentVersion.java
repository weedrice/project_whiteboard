package com.weedrice.whiteboard.domain.comment.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
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
@Table(name = "comment_versions")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
public class CommentVersion {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "comment_id", nullable = false)
    private Comment comment;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_id", nullable = false)
    private User modifier;

    @Column(name = "version_type", nullable = false, length = 50)
    private String versionType; // CREATE, MODIFY, DELETE

    @Column(name = "original_content", columnDefinition = "TEXT")
    private String originalContent;

    @CreatedDate
    @Column(name = "created_at", nullable = false, updatable = false)
    private LocalDateTime createdAt;

    @LastModifiedDate
    @Column(name = "modified_at", nullable = false)
    private LocalDateTime modifiedAt;

    @Builder
    public CommentVersion(Comment comment, User modifier, String versionType, String originalContent) {
        this.comment = comment;
        this.modifier = modifier;
        this.versionType = versionType;
        this.originalContent = originalContent;
    }
}
