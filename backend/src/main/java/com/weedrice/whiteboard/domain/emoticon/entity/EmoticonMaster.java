package com.weedrice.whiteboard.domain.emoticon.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import com.weedrice.whiteboard.domain.user.entity.User;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import jakarta.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "emoticon_masters", indexes = {
        @Index(name = "idx_emoticon_masters_active", columnList = "is_active, created_at"),
        @Index(name = "idx_emoticon_masters_creator", columnList = "creator_id")
})
public class EmoticonMaster extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "emoticon_id")
    private Long emoticonId;

    @Column(name = "name", length = 100, nullable = false)
    private String name;

    @Column(name = "thumbnail_url", length = 500)
    private String thumbnailUrl;

    @JdbcTypeCode(SqlTypes.ARRAY)
    @Column(name = "tags", columnDefinition = "text[]")
    private List<String> tags = new ArrayList<>();

    @Column(name = "is_active", length = 1, nullable = false)
    private String isActive;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "creator_id")
    private User creator;

    @Column(name = "purchase_count", nullable = false)
    private Integer purchaseCount;

    @OneToMany(mappedBy = "emoticonMaster", cascade = CascadeType.ALL, orphanRemoval = true)
    @OrderBy("sortOrder ASC, imageId ASC")
    private List<EmoticonImage> images = new ArrayList<>();

    @Builder
    public EmoticonMaster(String name, String thumbnailUrl, List<String> tags, User creator) {
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags != null ? tags : new ArrayList<>();
        this.isActive = "Y";
        this.creator = creator;
        this.purchaseCount = 0;
    }

    public void update(String name, String thumbnailUrl, List<String> tags) {
        this.name = name;
        this.thumbnailUrl = thumbnailUrl;
        this.tags = tags != null ? tags : new ArrayList<>();
    }

    public void activate() {
        this.isActive = "Y";
    }

    public void deactivate() {
        this.isActive = "N";
    }

    public void addImage(EmoticonImage image) {
        this.images.add(image);
        image.setEmoticonMaster(this);
    }

    public void removeImage(EmoticonImage image) {
        this.images.remove(image);
        image.setEmoticonMaster(null);
    }

    public boolean isOwner(Long userId) {
        return this.creator != null && this.creator.getUserId().equals(userId);
    }

    public void incrementPurchaseCount() {
        this.purchaseCount++;
    }
}
