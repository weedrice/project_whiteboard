package com.weedrice.whiteboard.domain.tag.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "tags", indexes = {
        @Index(name = "idx_tags_count", columnList = "post_count")
})
public class Tag extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "tag_id")
    private Long tagId;

    @Column(name = "tag_name", length = 100, nullable = false, unique = true)
    private String tagName;

    @Column(name = "post_count", nullable = false)
    private Integer postCount;

    @Builder
    public Tag(String tagName) {
        this.tagName = tagName;
        this.postCount = 0;
    }

    public void incrementPostCount() {
        this.postCount++;
    }

    public void decrementPostCount() {
        if (this.postCount > 0) {
            this.postCount--;
        }
    }
}
