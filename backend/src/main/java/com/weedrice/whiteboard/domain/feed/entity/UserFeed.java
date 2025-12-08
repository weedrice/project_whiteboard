package com.weedrice.whiteboard.domain.feed.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.converter.BooleanToYNConverter;
import com.weedrice.whiteboard.global.common.entity.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import jakarta.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "user_feeds", indexes = {
        @Index(name = "idx_user_feeds_user", columnList = "target_user_id, is_read, created_at DESC")
})
public class UserFeed extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "feed_id")
    private Long feedId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "target_user_id", nullable = false)
    private User targetUser;

    @Column(name = "feed_type", length = 50, nullable = false)
    private String feedType;

    @Column(name = "content_type", length = 50, nullable = false)
    private String contentType;

    @Column(name = "content_id", nullable = false)
    private Long contentId;

    @Column(name = "source_criteria", length = 50, nullable = false)
    private String sourceCriteria;

    @Column(name = "criteria_id")
    private Long criteriaId;

    @Convert(converter = BooleanToYNConverter.class)
    @Column(name = "is_read", length = 1, nullable = false)
    private Boolean isRead;

    @Builder
    public UserFeed(User targetUser, String feedType, String contentType, Long contentId, String sourceCriteria, Long criteriaId) {
        this.targetUser = targetUser;
        this.feedType = feedType;
        this.contentType = contentType;
        this.contentId = contentId;
        this.sourceCriteria = sourceCriteria;
        this.criteriaId = criteriaId;
        this.isRead = false;
    }

    public void read() {
        this.isRead = true;
    }
}
