package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "popular_posts", indexes = {
        @Index(name = "idx_popular_posts_type", columnList = "ranking_type, rank"),
        @Index(name = "idx_popular_posts_post", columnList = "post_id")
})
public class PopularPost extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "cache_id")
    private Long cacheId;

    @Column(name = "ranking_type", length = 50, nullable = false)
    private String rankingType; // DAILY, WEEKLY, MONTHLY

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "post_id", nullable = false)
    private Post post;

    @Column(name = "score", nullable = false)
    private Double score;

    @Setter
    @Column(name = "rank", nullable = false)
    private Integer rank;

    @Builder
    public PopularPost(String rankingType, Post post, Double score, Integer rank) {
        this.rankingType = rankingType;
        this.post = post;
        this.score = score;
        this.rank = rank;
    }
}
