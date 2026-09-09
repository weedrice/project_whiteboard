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

    @Column(name = "modifier_id", nullable = false)
    private Long modifierId;

    @Column(name = "version_type", length = 50, nullable = false)
    private String versionType; // CREATE, MODIFY, DELETE

    @Column(name = "original_title", length = 200)
    private String originalTitle;

    @Column(name = "original_contents", columnDefinition = "TEXT")
    private String originalContents;

    @Builder
    public PostVersion(Post post, Long modifierId, String versionType, String originalTitle, String originalContents) {
        this.post = post;
        this.modifierId = modifierId;
        this.versionType = versionType;
        this.originalTitle = originalTitle;
        this.originalContents = originalContents;
    }

    public static class PostVersionBuilder {
        public PostVersionBuilder modifier(UserIdRef modifier) {
            this.modifierId = modifier == null ? null : modifier.getUserId();
            return this;
        }
    }
}
