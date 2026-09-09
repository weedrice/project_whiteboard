package com.weedrice.whiteboard.domain.post.entity;

import com.weedrice.whiteboard.domain.actor.UserIdRef;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "post_series")
public class PostSeries extends BaseTimeEntity {
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "series_id")
    private Long seriesId;

    @Column(name = "owner_user_id", nullable = false)
    private Long ownerUserId;

    @Column(name = "title", nullable = false, length = 120)
    private String title;

    @Column(name = "description", length = 500)
    private String description;

    @Builder
    public PostSeries(Long ownerUserId, String title, String description) {
        this.ownerUserId = ownerUserId;
        this.title = title;
        this.description = description;
    }

    public static class PostSeriesBuilder {
        public PostSeriesBuilder owner(UserIdRef owner) {
            this.ownerUserId = owner == null ? null : owner.getUserId();
            return this;
        }
    }

    public void update(String title, String description) {
        if (title != null) {
            this.title = title;
        }
        this.description = description;
    }
}
