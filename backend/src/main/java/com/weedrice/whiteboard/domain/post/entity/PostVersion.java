package com.weedrice.whiteboard.domain.post.entity;

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
@Table(name = "post_versions", indexes = {
        @Index(name = "idx_post_versions_post", columnList = "post_id, created_at")
})
public class PostVersion extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "history_id")
    private Long historyId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "modifier_id", nullable = false)
    private User modifier;

    @Column(name = "version_type", length = 50, nullable = false)
    private String versionType; // CREATE, MODIFY, DELETE

    @Column(name = "original_title", length = 200)
    private String originalTitle;

    @Column(name = "original_contents", columnDefinition = "TEXT")
    private String originalContents;

    @Builder
    public PostVersion(Post post, User modifier, String versionType, String originalTitle, String originalContents) {
        this.post = post;
        this.modifier = modifier;
        this.versionType = versionType;
        this.originalTitle = originalTitle;
        this.originalContents = originalContents;
    }
}
