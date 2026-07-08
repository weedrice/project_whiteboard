package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "post_series_items")
public class PostSeriesItem extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "item_id")
    private Long itemId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "series_id", nullable = false)
    private PostSeries series;

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "sort_order", nullable = false)
    private Integer sortOrder = 0;

    @Builder
    public PostSeriesItem(PostSeries series, Post post, Integer sortOrder) {
        this.series = series;
        this.post = post;
        this.sortOrder = sortOrder == null ? 0 : sortOrder;
    }

    public void moveTo(PostSeries series, Integer sortOrder) {
        this.series = series;
        if (sortOrder != null) {
            this.sortOrder = sortOrder;
        }
    }
}
