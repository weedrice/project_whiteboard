package com.weedrice.whiteboard.domain.search.entity;

import com.weedrice.whiteboard.domain.user.entity.User;
import com.weedrice.whiteboard.global.common.BaseTimeEntity;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import javax.persistence.*;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@Entity
@Table(name = "search_personalization", indexes = {
        @Index(name = "idx_search_personalization_user", columnList = "user_id, created_at DESC")
})
public class SearchPersonalization extends BaseTimeEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "log_id")
    private Long logId;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User user;

    @Column(name = "keyword", length = 255, nullable = false)
    private String keyword;

    @Builder
    public SearchPersonalization(User user, String keyword) {
        this.user = user;
        this.keyword = keyword;
    }
}
